package org.hanuna.gitalk.gittree;

import org.hanuna.gitalk.gittree.CommitNode;

import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class CommitQueue {
     private Map<String, CommitNode> mapOfCommits;

    public CommitQueue() {
        mapOfCommits = new HashMap<String, CommitNode>();
    }

    public CommitNode getCommitNode(String hashCommit) {
        CommitNode c = mapOfCommits.get(hashCommit);
        if (c == null) {
            c = new CommitNode();
            mapOfCommits.put(hashCommit, c);
        }
        return c;
    }

    public CommitNode popCommitNode(String hashCommit) {
        CommitNode c = mapOfCommits.get(hashCommit);
        if (c == null) {
            c = new CommitNode();
        } else {
            mapOfCommits.remove(hashCommit);
        }
        return c;
    }


}
