import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

public class Git {
    public static void main(String[] args) {
        init();
        save("Hello.txt");
        save("test/test.txt");
        save("Hello.txt");

        ArrayList<String> indexList;
        try {
            indexList = makeIndexList();
            String testTreeHash = createTree(indexList, "test");
            System.out.println("Tree hash: " + testTreeHash);
        } catch (IOException e) {
            System.out.println(e);
        }
        

    }


    /*
     * Initializes the repository's necessary files. Creates the git directory with children
     * objects/, HEAD and index. Returns true if the repository could be created, returns false if
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
     * Hashes a file based on its contents. Returns a SHA-1 hash unique to this file's contents.
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

    /*
     * Creates a blob file for the given filepath and stores it inside the objects folder. This
     * blob's name and its corresponding file name is then stored in index. Returns true if the file
     * could be saved to objects, returns false otherwise.
     */
    public static boolean save(String filePath) {
        try {

            String hash = hashFile(filePath);
            

            if (!checkSave(filePath, hash)) { //filePath changed to relativePath
                return false;
            }

            File blob = new File("git/objects/" + hash);
            FileReader reader = new FileReader(filePath);
            FileWriter writer = new FileWriter(blob);
            int c;

            // if (!filePath.contains("/")) {
            //     writer.write("blob ");
            // } else if (filePath.contains("/")) {
            //     makeTreeFile(filePath); //check!!
            // }

            while ((c = reader.read()) != -1) {
                writer.append((char) (c));
            }
            reader.close();
            writer.close();

            if (index(hash, filePath)) { //filePath changed to relativePath
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

    // public static String makeTreeFile(String filePath) {
    //     if (filePath.contains("/")) {
    //         String[] files = filePath.split("/");
    //         for (int i = files.length - 1; i >= 0; i--) {
    //             save(files[i]);
    //             //check if this file is a directory
    //             //go look at the files in it
    //             //hash them and add them to inside the fiel in the objects folder with the title blob or directory depending on what they are
    //             // then rehash the file with the new things addded inside of them
    //             //then update the hash and replace the old hash of this directory inside the file of the directory it is in
    //             //rehash that director
    //         }
    //         hashFile(files[files.length - 1]);

    //     }
    // }

    public static ArrayList<String> makeIndexList() throws IOException {
        ArrayList<String> indexList = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("git/index"))) {
            if (!line.isEmpty()) {
                indexList.add("blob " + line);
            }
        }
        return indexList;
    }

    public static String createTree(ArrayList<String> indexList, String path) {
        StringBuilder contents = new StringBuilder();

        for (String part : indexList) {
            String[] parts = part.split(" ", 3);
            String partPath = parts[2];
            // int slashNumber = 0;
            // for (int i = 0; i < partPath.length(); i++) {
            //     if (partPath.charAt(i) == '/') {
            //         slashNumber++;
            //     }
            // }
            int lastSlashIndex = partPath.lastIndexOf('/');
            String parent = "";
            String fileName = partPath;
            if (lastSlashIndex != -1) {
                parent = partPath.substring(0, lastSlashIndex);
                fileName = partPath.substring(lastSlashIndex + 1);
            }

            if (parent.equals(path)) {
                if (contents.length() >0) {
                    contents.append("\n");
                }
                contents.append(parts[0] + " " + parts[1] + " " + fileName);
            }
        }

        String fileHash = hashString(contents.toString());
        try {
            FileWriter writer = new FileWriter("git/objects/" + fileHash);
            writer.write(contents.toString());
            writer.close();
        } catch (Exception e) {
            System.out.println("Failed to write tree in objects: " + e);
            return null;
        }

        return fileHash;
    }    

    /*
     * Checks if the current filepath and hash combination already exists in index to prevent
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
     * Stores a file's hash and its path in the index file. Returns true if successful, otherwise
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
     * Checks if the current filepath exists but the hash has changed. Returns the line number of
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
}