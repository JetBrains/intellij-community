package org.hanuna.gitalk;

import org.hanuna.gitalk.commitgraph.builder.CommitRowListBuilder;
import org.hanuna.gitalk.commitgraph.builder.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
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
public class RunUI {

    public static void main(String args[]) throws IOException {
        Date date = new Date();
        long time = date.getTime();

        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        ReadOnlyList<Commit> list = GitLogParser.parseCommitLog(r);
        date = new Date();
        System.out.println(date.getTime() - time);
        time = date.getTime();

        int k = 0;
        int count = 0;
        int countBadEdges = 0;
        for (Commit c : list) {
            for (Commit par : c.getParents()) {
                int l = par.index() - c.index();
                if (l > 10) {
                    ReadOnlyList<Commit> childrens = par.getChildren();
                    if (childrens.size() > 10) {
                        if (childrens.get(1).index() - childrens.get(0).index() > 20 &&
                                childrens.get(2).index() - childrens.get(1).index() > 20) {
                            countBadEdges++;
                            System.out.println(par.getMessage());
                            for (Commit ch : childrens) {
                                System.out.println("   " + ch.getMessage());
                            }
                        }
                    }
                    count++;
                }
                if (k < l) {
                    k = l;
                }
            }
        }
        System.out.println("max r:" + k);

        System.out.println("count:" + count);
        System.out.println("countBadEdges:" + countBadEdges);

        date = new Date();
        System.out.println(date.getTime() - time);

        if (1 == 0) {
            CommitRowListBuilder builder = new CommitRowListBuilder(list);
            List<RowOfNode> rows = builder.buildListLineOfNode();


            date = new Date();
            System.out.println(date.getTime() - time);
            System.out.println(list.size());

            new GitAlkUI(builder.build(), list);
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
                System.out.println(line.getStartIndexColor() + " " + line.getMainPosition() + " " + line.getCountAdditionEdges());
            }
        }*/
    }
}
