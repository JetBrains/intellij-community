package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.CommitLine;
import org.hanuna.gitalk.commitgraph.CommitLineList;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitLineListAdapter implements CommitLineList {
    private final List<LineOfNode> lines;

    public CommitLineListAdapter(List<LineOfNode> lines) {
        this.lines = lines;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public CommitLine get(int index) {
        return null;
    }
}
