package org.hanuna.gitalk;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.builder.CommitRowListBuilder;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.parser.GitLogParser;
import org.hanuna.gitalk.swingui.GitAlkUI;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;

/**
 * @author erokhins
 */
public class RunUI {

    public static void main(String args[]) throws IOException {

        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        GitLogParser parser = new GitLogParser(r);

        Timer t = new Timer("log parse");
        CommitsModel commitsModel = parser.getFirstPart();
        t.print();

        Timer precalc = new Timer("precalculate");
        CommitRowListBuilder builder = new CommitRowListBuilder(commitsModel);
        ReadOnlyList<CommitRow> commitRows = builder.build();
        precalc.print();

        final GitAlkUI ui = new GitAlkUI(commitRows, commitsModel);
        ui.showUi();

        if (!commitsModel.isFullModel()) {
            Timer fullLogParse = new Timer("full log parse");
            final CommitsModel fullCommitsModel = parser.getFullModel();
            fullLogParse.print();

            Timer fullprecalc = new Timer("full precalculate");
            CommitRowListBuilder fullBuilder = new CommitRowListBuilder(fullCommitsModel);
            final ReadOnlyList<CommitRow> fullCommitRow = fullBuilder.build();
            fullprecalc.print();

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ui.update(fullCommitRow, fullCommitsModel);
                }
            });

            int maxL = 0;
            int c1 = 0;
            for (int i = 0; i < fullCommitsModel.size(); i++) {
                Commit upCommit = fullCommitsModel.get(i);
                if (upCommit.getChildren().size() != 1 || upCommit.getParents().size() != 1) {
                    for (int j = 0; j < upCommit.getParents().size(); j++) {
                        int k = 0;
                        Commit tC = upCommit.getParents().get(j);
                        while (tC.getChildren().size() == 1 && tC.getParents().size() == 1) {
                            k++;
                            tC = tC.getParents().get(0);
                        }
                        if (k > maxL) {
                            maxL = k;
                            System.out.println(maxL);
                        }
                        if (k > 10) {
                            c1++;
                        }
                    }
                }
            }
            System.out.println(c1);
        }

        /*
        if (1 == 1) {
            CommitRowListBuilder builder = new CommitRowListBuilder(commitsModel);
            ReadOnlyList<CommitRow> commitRows = builder.build();

            precalc.print();


       /* if (1 == 0) {
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
         */




        /*
        if (1 == 0) {
            for (int i = 0; i < commitsModel.size(); i++) {
                Commit node = commitsModel.get(i);
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
