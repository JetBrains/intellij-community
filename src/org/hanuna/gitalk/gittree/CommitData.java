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
        date = Integer.parseInt(sl.next());
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


    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(hash).append('|');
        s.append(mainParent).append('|');
        s.append(secondParent).append('|');
        s.append(date).append('|');
        s.append(author).append('|');
        s.append(message);

        return s.toString();
    }
}
