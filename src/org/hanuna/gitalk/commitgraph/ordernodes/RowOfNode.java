package org.hanuna.gitalk.commitgraph.ordernodes;

import org.hanuna.gitalk.commitgraph.node.Node;
import org.hanuna.gitalk.commitgraph.node.PositionNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.Row;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 *
 * Example of structire data:
 * input log is:
 * 0|-3 1|-
 * 1|-2 4|-
 * 2|-3 5|-
 * 3|-6|-
 * 4|-7|-
 * 5|-7|-
 * 6|-7|-
 * 7|-|-
 *
 * Lines was:
 *  0:0         0
 *  3:0 1:1     1
 *  3:0 2:1 4:2 2
 *  3:0 5:3 4:2 3
 *  6:0 5:3 4:2 3
 *  6:0 5:3 7:2 3
 *  6:0 7:3     3
 *  7:0         3
 *
 */
public interface RowOfNode extends ReadOnlyList<Node>, Row {
    public int getLastColorIndex();
    public int getIndexOfCommit(@NotNull Commit commit);

    @Nullable
    public PositionNode getPositionNode(@NotNull Commit commit);
}
