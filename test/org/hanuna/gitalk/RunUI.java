package org.hanuna.gitalk;

import org.hanuna.gitalk.common.Timer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author erokhins
 */
public class RunUI {

    public static void main(String args[]) throws IOException {

        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        Timer t = new Timer("git run");

        System.out.println("start");
        int c = 0;

        Timer period = new Timer("period");
        while (r.readLine() != null) {
            c++;
            if (c % 10000 == 0) {
                period.print();
                period.clear();
            }
        }

        t.print();





        /*
        if (1 == 1) {
            CommitRowListBuilder builder = new CommitRowListBuilder(commitsModel);
            ReadOnlyList<CommitRow> commitRows = builder.build();

            precalc.print();


       /* if (1 == 0) {
            ReadOnlyList<IndexedRowOfNode> rows = builder.rowsModel;
            for (IndexedRowOfNode row : rows) {
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
         */




        /*
        if (1 == 0) {
            for (int i = 0; i < commitsModel.size(); i++) {
                Commit node = commitsModel.get(i);
                System.out.println(toStr(node));
            }
            System.out.println();
            for (IndexedRowOfNode line : rows) {
                for (Node node : line) {
                    System.out.print(node.getCommitIndex() + ":" + node.getColorIndex() + " ");
                }
                System.out.println(line.getLastColorIndex() + " " + line.getMainPosition() + " " + line.getCountAdditionEdges());
            }
        }*/
    }
}
