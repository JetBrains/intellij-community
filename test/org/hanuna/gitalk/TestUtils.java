package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.builder.CommitLogData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;

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
    public static String toStr(CommitLogData cd) {
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
        CommitData data = node.getData();
        if (data == null) {
            throw new IllegalStateException();
        }
        for (Commit commit : data.getParents()) {
            sb.append(commit.hash().toStrHash()).append("|-");
        }
        sb.append(data.getMessage()).append("|-");
        sb.append(data.getAuthor()).append("|-");
        sb.append(data.getTimeStamp()).append("|-");
        return sb.toString();
    }

    public static String toShortStr(Commit node) {
        StringBuilder sb = new StringBuilder();
        CommitData data = node.getData();
        if (data == null) {
            throw new IllegalStateException();
        }
        sb.append(data.getLogIndex());
        sb.append("|-");
        for (Commit commit : data.getParents()) {
            sb.append(commit.getData().getLogIndex()).append("|-");
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
