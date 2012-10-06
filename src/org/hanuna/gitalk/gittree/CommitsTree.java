package org.hanuna.gitalk.gittree;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitsTree {
    private final List<CommitNode> listOfCommit = new ArrayList<CommitNode>();

    public CommitsTree(BufferedReader r) throws IOException {
        CommitQueue queue = new CommitQueue();
        String currentCommitLine;
        while ((currentCommitLine = r.readLine()) != null) {
            CommitData cData = new CommitData(currentCommitLine);
            CommitNode cNode = queue.popCommitNode(cData.getHash());
            cNode.setData(cData);

            //  check hash parent of Commit & put empty parent to queue & set link to empty parent node
            if (cData.getMainParentHash() != null) {
                cNode.setMainParent(queue.getCommitNode(cData.getMainParentHash()));
            }
            if (cData.getSecondParentHash() != null) {
                cNode.setSecondParent(queue.getCommitNode(cData.getSecondParentHash()));
            }
            cNode.setLogIndex(listOfCommit.size());
            listOfCommit.add(cNode);
        }
    }


    public CommitNode getNode(int index) {
        return listOfCommit.get(index);
    }

    public int size() {
        return listOfCommit.size();
    }



}
