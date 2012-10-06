package org.hanuna.gitalk.gittree;

/**
 * @author erokhins
 */
public class CommitData {
    private final String mainParent, secondParent, hash, author, message;
    private final int date;

    public CommitData(String commitLine) {
        SimpleLexer sl = new SimpleLexer(commitLine);
        hash = sl.next();

        String parents = sl.next();
        int k = parents.indexOf(' ');
        if (k != -1) {
            mainParent = parents.substring(0, k);
            secondParent = parents.substring(k + 1, parents.length());
        } else {
            mainParent = parents;
            secondParent = "";
        }

        author = sl.next();
        try {
            date = Integer.parseInt(sl.next());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted unix date, but found \"" + sl.currentString() + "\"");
        }
        message = sl.restString();
    }

    public String getHash() {
        return hash;
    }

    public String getMainParentHash() {
        if (mainParent.length() == 0) {
            return null;
        }
        return mainParent;
    }

    public String getSecondParentHash() {
        if (secondParent.length() == 0) {
            return null;
        }
        return secondParent;
    }

    public int getDate() {
        return date;
    }

    public String getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }

}
