import java.io.IOException;

public class GitWrapper {

    /**
     * Initializes a new Git repository. This method creates the necessary directory structure and
     * initial files (index, HEAD) required for a Git repository.
     */
    public void init() {
        Git.init();
    }

    /**
     * Stages a file for the next commit. This method adds a file to the index file. If the file
     * does not exist, it throws an IOException. If the file is a directory, it throws an
     * IOException. If the file is already in the index, it does nothing. If the file is
     * successfully staged, it creates a blob for the file.
     * 
     * @param filePath The path to the file to be staged.
     */
    public void add(String filePath) {
        Git.add(filePath);
    }

    /**
     * Creates a commit with the given author and message. It should capture the current state of
     * the repository by building trees based on the index file, writing the tree to the objects
     * directory, writing the commit to the objects directory, updating the HEAD file, and returning
     * the commit hash.
     *
     * The commit should be formatted as follows: tree: <tree_sha> parent: <parent_sha> author:
     * <author> date: <date> message: <message>
     *
     * @param author The name of the author making the commit.
     * @param message The commit message describing the changes.
     * @return The SHA1 hash of the new commit.
     */
    public String commit(String author, String message) throws IOException {
        return Git.commit(author, message);
    }

    /**
     * Checks out a specific commit given its hash. This method should read the HEAD file to
     * determine the "checked out" commit. Then it should update the working directory to match the
     * state of the repository at that commit by tracing through the root tree and all its children.
     *
     * @param commitHash The SHA1 hash of the commit to check out.
     */
    public void checkout(String commitHash) throws IOException {
        Git.checkout(commitHash);
    }
}
