package org.hanuna.gitalk;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.Node;
import org.hanuna.gitalk.commitgraph.builder.CommitRowListBuilder;
import org.hanuna.gitalk.commitgraph.hidecommits.HideCommits;
import org.hanuna.gitalk.commitgraph.ordernodes.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.parser.GitLogParser;
import org.hanuna.gitalk.swingui.GitAlkUI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.hanuna.gitalk.TestUtils.toStr;

/**
 * @author erokhins
 */
public class RunUI {

    public static void main(String args[]) throws IOException {
        Timer t = new Timer("log parse");

        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        CommitsModel list = GitLogParser.parseCommitLog(r);

        t.print();

        Timer t1 = new Timer("precalculate");

        if (1 == 1) {
            CommitRowListBuilder builder = new CommitRowListBuilder(list);
            ReadOnlyList<CommitRow> commitRows = builder.build();

            t1.print();


        if (1 == 0) {
            ReadOnlyList<RowOfNode> rows = builder.rowsModel;
            for (RowOfNode row : rows) {
                for (Node node : row) {
                    System.out.print(toStr(node.getCommit().hash()) + ":" + node.getColorIndex() + " ");
                }
                System.out.println(row.getLastColorIndex());
            }
        }

        if (1 == 0) {
            ReadOnlyList<HideCommits> hidesCommits = builder.hideModel;
            for (HideCommits hides : hidesCommits) {
                for (Commit commit : hides) {
                    System.out.print(toStr(commit.hash()) + ":" + commit.getMessage() + " ");
                }
                System.out.println();
            }
        }


            new GitAlkUI(commitRows, list);
        }


        /*
        if (1 == 0) {
            for (int i = 0; i < list.size(); i++) {
                Commit node = list.get(i);
                System.out.println(toStr(node));
            }
            System.out.println();
            for (RowOfNode line : rows) {
                for (Node node : line) {
                    System.out.print(node.getCommitIndex() + ":" + node.getColorIndex() + " ");
                }
                System.out.println(line.getLastColorIndex() + " " + line.getMainPosition() + " " + line.getCountAdditionEdges());
            }
        }*/
    }
}
