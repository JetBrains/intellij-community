package org.hanuna.gitalk.commitgraph.builder;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.hides.HideCommits;
import org.hanuna.gitalk.commitgraph.hides.HideCommitsCalculator;
import org.hanuna.gitalk.commitgraph.order.RowOfNode;
import org.hanuna.gitalk.commitgraph.order.RowOfNodeCalculator;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.CalculateModel;
import org.hanuna.gitalk.common.calculatemodel.FullPreCalculateModel;

/**
 * @author erokhins
 */
public class CommitRowListBuilder {
    private final CommitsModel commitsModel;
    public CalculateModel<RowOfNode> rowsModel = new FullPreCalculateModel<RowOfNode>();
    public CalculateModel<HideCommits> hideModel = new FullPreCalculateModel<HideCommits>();
    private int size;

    public CommitRowListBuilder(@NotNull CommitsModel commitsModel) {
        this.commitsModel = commitsModel;
        size = commitsModel.size();
    }

    @NotNull
    public ReadOnlyList<CommitRow> build() {
        rowsModel.prepare(new RowOfNodeCalculator(commitsModel), size);
        hideModel.prepare(new HideCommitsCalculator(commitsModel), size);
        return new CommitRowListAdapter(rowsModel, hideModel, size, commitsModel);
    }



}
