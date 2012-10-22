package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.ReadOnlyList;

/**
 * @author erokhins
 */
public class TestUtils {

    public static String toStr(Hash hash) {
        if (hash == null) {
            return "null";
        } else {
            return hash.toStrHash();
        }
    }
    public static String toStr(CommitData cd) {
        StringBuilder sb = new StringBuilder();
        sb.append(toStr(cd.getHash())).append("|-");
        for (Hash hash : cd.getParentsHash()) {
            sb.append(hash.toStrHash()).append("|-");
        }
        sb.append(cd.getAuthor()).append("|-");
        sb.append(cd.getTimeStamp()).append("|-");
        sb.append(cd.getCommitMessage());
        return sb.toString();
    }

    public static String toStr(Commit node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.hash().toStrHash()).append("|-");
        for (Commit commit : node.getParents()) {
            sb.append(commit.hash().toStrHash()).append("|-");
        }
        sb.append(node.getMessage()).append("|-");
        sb.append(node.getAuthor()).append("|-");
        sb.append(node.getTimeStamp()).append("|-");
        sb.append(node.hasChildren()).append("|-");
        sb.append(node.countNewUniqueCommitsAmongParents());
        return sb.toString();
    }

    public static String toShortStr(Commit node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.index()).append("|-");
        for (Commit commit : node.getParents()) {
            sb.append(commit.index()).append("|-");
        }
        sb.append(node.hash().toStrHash());
        return sb.toString();
    }

    public static String toShortStr(ReadOnlyList<Commit> commits) {
        StringBuilder sb = new StringBuilder();
        for (Commit commit : commits) {
            sb.append(toShortStr(commit)).append("\n");
        }
        return sb.toString();
    }


}
