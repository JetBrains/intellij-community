package org.hanuna.gitalk;

import org.hanuna.gitalk.gittree.CommitData;
import org.hanuna.gitalk.gittree.CommitNode;
import org.hanuna.gitalk.gittree.CommitsTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author erokhins
 */
public class testUtils {
    public static String toStr(CommitData d) {
        StringBuilder s = new StringBuilder();
        s.append(d.getHash()).append('|');

        s.append(d.getMainParentHash()).append('|');
        s.append(d.getSecondParentHash()).append('|');

        s.append(d.getAuthor()).append('|');
        s.append(d.getDate()).append('|');
        s.append(d.getMessage());

        return s.toString();
    }


    public static void main(String args[]) throws IOException {
        Process p = Runtime.getRuntime().exec("git log --all --format=\"%h|-%p|-%an|-%ct|-%s\"");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        CommitsTree ct = new CommitsTree(r);
        for (int i = 0; i < ct.size(); i++) {
            CommitNode cn = ct.getNode(i);
            System.out.println(toStr(cn.getData()));
        }
    }

    public static String toShortStr(CommitNode n) {
        StringBuilder s = new StringBuilder();
        s.append(n.getLogIndex()).append('|');

        if (n.getMainParent() != null) {
            s.append(n.getMainParent().getLogIndex()).append('|');
        }
        if (n.getSecondParent() != null) {
            s.append(n.getSecondParent().getLogIndex()).append('|');
        }
        s.append(n.getData().getHash());

        return s.toString();
    }

    public static String toShortStr(CommitsTree t) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < t.size(); i++) {
            s.append(toShortStr(t.getNode(i))).append("\n");
        }
        return s.toString();
    }
}
