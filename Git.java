import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import javax.lang.model.element.UnknownElementException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

public class Git {
    public static void main(String[] args) throws IOException {
        var commit = commit("Dariush", "Commit");
        checkout(commit);
    }

    // tree: 8c411a89ed6d846f064ed0decdba3a857f0d1667
    // parent: 2b98fbd4f414b26b612fa50b17879f62733254e6
    // author: Mr. Theiss
    // date: Thu Oct 09 11:56:39 PDT 2025
    // message: Add initial project files

    public static void checkout(String commitHash) throws IOException {
        var commitLines = Files.readAllLines(Path.of("git/objects/" + commitHash));
        if (commitLines.size() == 0)
            throw new IOException();

        var treeHash = commitLines.get(0).split(" ")[1];
        var treeIndex = Files.readAllLines(Path.of("git/objects/" + treeHash));

        var trackedFiles = trackedFiles();
        for (var file : trackedFiles) {
            Files.deleteIfExists(file);
        }

        buildFromIndex(treeIndex.get(0).split(" ")[1], "");
    }

    // rebuilds file tree from index file for checking out commits
    private static void buildFromIndex(String treeHash, String currentDir) throws IOException {
        var lines = Files.readAllLines(Path.of("git/objects/" + treeHash));

        for (var line : lines) {
            var entry = line.split(" ");
            switch (entry[0]) {
                case "blob":
                    Files.write(Path.of(currentDir + entry[2]),
                            Files.readAllBytes(Path.of("git/objects/" + entry[1])));
                    break;
                case "tree":
                    Files.createDirectories(Path.of(entry[2]));
                    buildFromIndex(entry[1], currentDir + entry[2] + "/");
                    break;
            }
        }
    }

    // goes throguh HEAD and returns a hashset of files that are inside the commit
    // so we know if we need to delete a file or not and that kind of stuff
    private static HashSet<Path> trackedFiles() throws IOException {
        var files = new HashSet<Path>();
        var recent = Files.readAllLines(Path.of("git/HEAD"));
        if (recent.isEmpty())
            throw new IOException();
        var lastCommitHash = recent.get(0);
        goThroughCommitToMakeMapOfTrackedFiles(lastCommitHash, lastCommitHash, files);
        return files;
    }

    // goes through commit to make map of tracked files
    private static void goThroughCommitToMakeMapOfTrackedFiles(String treeHash, String currentDir, HashSet<Path> files)
            throws IOException {
        var lines = Files.readAllLines(Path.of("git/objects/" + treeHash));
        for (var line : lines) {
            var entry = line.split(" ");
            switch (entry[0]) {
                case "blob":
                    files.add(Path.of(currentDir + entry[2]));
                    break;
                case "tree":
                    // files.add(Path.of(currentDir + entry[2] + "/"));
                    goThroughCommitToMakeMapOfTrackedFiles(entry[1], currentDir + entry[2] + "/", files);
                    break;
            }
        }
    }

    public static String commit(String author, String message) throws IOException {
        ArrayList<String> indexList;
        indexList = makeIndexList();
        String testTreeHash = createTree(indexList, "");
        // System.out.println("Tree hash: " + testTreeHash);

        String hashFromIndex = createTreeFromIndex();
        // System.out.println("Root tree hash: " + hashFromIndex);

        var parentEntry = "parent: ";
        var commitEntry = "tree: " + hashFromIndex;
        var parent = Files.readAllLines(Path.of("git/HEAD"));
        if (parent.size() > 0) {
            parentEntry += parent.get(0);
        }

        var date = LocalDate.now();

        var commit = commitEntry + "\n" + parentEntry + "\n" + "author: " + author + "\ndate: "
                + date + "\nmessage: " + message;

        var hash = hashString(commit);
        Files.write(Path.of("git/objects/" + hash), commit.getBytes());
        Files.writeString(Path.of("git/HEAD"), hash);
        Files.writeString(Path.of("git/index"), "");
        return hash;
    }

    /*
     * Initializes the repository's necessary files. Creates the git directory with
     * children
     * objects/, HEAD and index. Returns true if the repository could be created,
     * returns false if
     * not or if the current repo already exists
     */
    public static boolean init() {
        File git = new File("./git/");
        if (!git.mkdir()) {
            System.out.println("Git Repository Already Exists");
            return false;
        }

        File objects = new File("./git/objects/");
        File head = new File("./git/HEAD");
        File index = new File("./git/index");

        try {
            if (!objects.mkdir() || !head.createNewFile() || !index.createNewFile()) {
                System.out.println("Git Repository Already Exists");
                return false;
            }
        } catch (IOException e) {
            System.out.println("Failed to create Git Repository");
            return false;
        }

        System.out.println("Git Repository Created at " + git.getParentFile().getAbsolutePath());
        return true;
    }

    /*
     * Hashes a file based on its contents. Returns a SHA-1 hash unique to this
     * file's contents.
     */
    public static String hashFile(String filePath) {
        try {
            StringBuilder contents = new StringBuilder();
            FileReader reader = new FileReader(filePath);
            int c;
            while ((c = reader.read()) != -1) {
                contents.append((char) (c));
            }
            reader.close();

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(contents.toString().getBytes());
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            System.out.println("File could not be hashed");
            return null;
        }
    }

    public static String hashString(String contents) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(contents.getBytes());
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    public static void add(String filePath) {
        addFile(filePath);
    }

    /*
     * Creates a blob file for the given filepath and stores it inside the objects
     * folder. This
     * blob's name and its corresponding file name is then stored in index. Returns
     * true if the file
     * could be saved to objects, returns false otherwise.
     */
    private static boolean addFile(String filePath) {
        try {

            String hash = hashFile(filePath);

            if (!checkSave(filePath, hash)) {
                return false;
            }

            File blob = new File("git/objects/" + hash);
            FileReader reader = new FileReader(filePath);
            FileWriter writer = new FileWriter(blob);
            int c;

            while ((c = reader.read()) != -1) {
                writer.append((char) (c));
            }
            reader.close();
            writer.close();

            if (index(hash, filePath)) {
                return true;
            } else {
                blob.delete();
                System.out.println("Failed to save changes to file at " + filePath);
                return false;
            }

        } catch (Exception e) {
            System.out.println("Failed to save changes to file at " + filePath);
            return false;
        }
    }

    /*
     * Checks if the current filepath and hash combination already exists in index
     * to prevent
     * overwriting. Rewriting changed hashes is handled in getRewriteIndex()
     */
    private static boolean checkSave(String filePath, String hash) {
        try {
            BufferedReader index = new BufferedReader(new FileReader("git/index"));
            String str;
            while ((str = index.readLine()) != null) {
                if (str.equals(hash + " " + filePath)) {
                    index.close();
                    return false;
                }
            }
            index.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Stores a file's hash and its path in the index file. Returns true if
     * successful, otherwise
     * returns false.
     */
    public static boolean index(String hash, String filePath) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("./git/index"));

            StringBuilder contents = new StringBuilder();
            String str;
            int currLine = 0;
            int rewriteLine = getRewriteIndex(hash, filePath);

            while ((str = reader.readLine()) != null) {
                if (currLine > 0) {
                    contents.append("\n");
                }

                if (currLine == rewriteLine) {
                    contents.append(hash + " " + filePath); //
                } else {
                    contents.append(str);
                }
                currLine++;
            }
            reader.close();

            if (rewriteLine == -1) {
                if (contents.length() > 0) {
                    contents.append("\n");
                }
                contents.append(hash + " " + filePath); //
            }

            FileWriter writer = new FileWriter("./git/index", false);
            writer.write(contents.toString());
            writer.close();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Checks if the current filepath exists but the hash has changed. Returns the
     * line number of
     * that stale entry, or -1 if no such entry exists.
     */
    public static int getRewriteIndex(String hash, String filePath) {
        try {
            BufferedReader index = new BufferedReader(new FileReader("git/index"));
            String str;
            int i = 0;
            while ((str = index.readLine()) != null) {
                int spaceIdx = str.indexOf(' ');
                if (spaceIdx != -1) {
                    String currHash = str.substring(0, spaceIdx);
                    String currPath = str.substring(spaceIdx + 1);
                    if (currPath.equals(filePath) && !currHash.equals(hash)) {
                        index.close();
                        return i;
                    }
                }
                i++;
            }
            index.close();
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    // Copies index file to make a temporary list
    public static ArrayList<String> makeIndexList() throws IOException {
        ArrayList<String> indexList = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("git/index"))) {
            if (!line.isEmpty()) {
                indexList.add("blob " + line);
            }
        }
        sortByPath(indexList);
        return indexList;
    }

    // Creates tree for one directory by going through indexList and finding objects
    // with same
    // parent. Adds all of them to contents, then hashes entire contents.
    public static String createTree(ArrayList<String> indexList, String path) {
        StringBuilder contents = new StringBuilder();

        for (String line : indexList) {
            String[] parts = line.split(" ", 3);
            String linePath = parts[2];

            int lastSlashIndex = linePath.lastIndexOf('/');
            String parent = "";
            String fileName = linePath;
            if (lastSlashIndex != -1) {
                parent = linePath.substring(0, lastSlashIndex);
                fileName = linePath.substring(lastSlashIndex + 1);
            }

            if (parent.equals(path)) { // Checks if each line in indexList is in parent folder, adds
                                       // if so
                if (contents.length() > 0) {
                    contents.append("\n");
                }
                contents.append(parts[0] + " " + parts[1] + " " + fileName);
            }
        }

        String fileHash = hashString(contents.toString());
        try {
            FileWriter writer = new FileWriter("git/objects/" + fileHash); // adds tree to objects
                                                                           // folder
            writer.write(contents.toString());
            writer.close();
        } catch (Exception e) {
            System.out.println("Failed to write tree in objects: " + e);
            return null;
        }

        return fileHash;
    }

    // gets parent folder of file from path, returns "" if at first level
    public static String getParent(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return "";
        } else {
            return path.substring(0, lastSlashIndex);
        }
    }

    // sorts alphabetically
    public static void sortByPath(ArrayList<String> list) {
        for (int i = 0; i < list.size(); i++) {
            int little = i;

            for (int j = i + 1; j < list.size(); j++) {
                String[] partsJ = list.get(j).split(" ", 3);
                String jPath = partsJ[2];

                String[] partsSmallest = list.get(little).split(" ", 3);
                String smallestPath = partsSmallest[2];

                if (jPath.compareTo(smallestPath) < 0) {
                    little = j;
                }
            }

            String temp = list.get(i);
            list.set(i, list.get(little));
            list.set(little, temp);
        }
    }

    // gets folder of deepest entry or returns "" if everything is at same first
    // level
    public static String findDeepestFolder(ArrayList<String> indexList) {
        int numSlashesMax = -1;
        String deepestPath = "";

        for (String line : indexList) {
            String[] parts = line.split(" ", 3);
            String linePath = parts[2];

            int slashCounter = 0;
            for (int i = 0; i < linePath.length(); i++) {
                if (linePath.charAt(i) == '/') {
                    slashCounter++;
                }
            }

            if (slashCounter > numSlashesMax) {
                numSlashesMax = slashCounter;
                deepestPath = linePath;
            }
        }

        return getParent(deepestPath);
    }

    public static String createTreeFromIndex() throws IOException {
        ArrayList<String> indexList = makeIndexList();
        return collapseList(indexList);
    }

    public static String collapseList(ArrayList<String> indexList) {
        String folder = findDeepestFolder(indexList);
        String treeHash = createTree(indexList, folder);

        // if everything on same first level, return just the hash of all those contents
        if (folder.equals("")) {
            return treeHash;
        }

        // copys everything not in the same folder as deepest file to new list
        ArrayList<String> newList = new ArrayList<>();
        for (String line : indexList) {
            String[] parts = line.split(" ", 3);
            String linePath = parts[2];

            if (!getParent(linePath).equals(folder)) {
                newList.add(line);
            }
        }
        newList.add("tree " + treeHash + " " + folder);
        sortByPath(newList);

        return collapseList(newList);

    }

}
