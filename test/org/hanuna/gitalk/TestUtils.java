package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;

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
        sb.append(toStr(cd.getMainParentHash())).append("|-");
        sb.append(toStr(cd.getSecondParentHash())).append("|-");
        sb.append(cd.getAuthor()).append("|-");
        sb.append(cd.getTimeStamp()).append("|-");
        sb.append(cd.getCommitMessage());
        return sb.toString();
    }
}
