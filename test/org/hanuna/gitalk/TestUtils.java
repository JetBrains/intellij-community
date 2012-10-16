package org.hanuna.gitalk;

import org.hanuna.gitalk.commitgraph.builder.CommitRowListBuilder;
import org.hanuna.gitalk.commitgraph.builder.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.CommitList;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.parser.GitLogParser;
import org.hanuna.gitalk.swingui.GitAlkUI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;

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

    public static String toStr(Commit node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.index()).append("|-");
        if (node.mainParent() != null) {
            sb.append(node.mainParent().index()).append("|-");
        } else {
            sb.append("null|-");
        }
        if (node.secondParent() != null) {
            sb.append(node.secondParent().index()).append("|-");
        } else {
            sb.append("null|-");
        }
        sb.append(toStr(node.getData()));
        return sb.toString();
    }

    public static void main(String args[]) throws IOException {
        Date date = new Date();
        long time = date.getTime();

        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        CommitList list = GitLogParser.parseCommitLog(r);

        CommitRowListBuilder builder = new CommitRowListBuilder(list);
        List<RowOfNode> rows = builder.buildListLineOfNode();


        date = new Date();
        System.out.println(date.getTime() - time);
        System.out.println(rows.size());

        new GitAlkUI(builder.build(), list);
        /*
        for (int i = 0; i < list.size(); i++) {
            Commit node = list.get(i);
            System.out.println(toStr(node));
        }
        System.out.println();
        for (RowOfNode line : rows) {
            for (GraphNode node : line) {
                System.out.print(node.getIndexCommit() + ":" + node.getIndexColor() + " ");
            }
            System.out.println(line.getAdditionColor() + " " + line.getMainPosition());
        }
        */
    }

}
