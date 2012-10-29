package org.hanuna.gitalk.commitgraph.builder;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.hidecommits.HideCommits;
import org.hanuna.gitalk.commitgraph.hidecommits.HideCommitsCalculator;
import org.hanuna.gitalk.commitgraph.ordernodes.RowOfNode;
import org.hanuna.gitalk.commitgraph.ordernodes.RowOfNodeCalculator;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.CalculateModel;
import org.hanuna.gitalk.common.calculatemodel.PartSaveCalculateModel;

/**
 * @author erokhins
 */
public class CommitRowListBuilder {
    private final CommitsModel commitsModel;

    public CommitRowListBuilder(@NotNull CommitsModel commitsModel) {
        this.commitsModel = commitsModel;
    }

    //when run this method commitsModel must be constant
    @NotNull
    public ReadOnlyList<CommitRow> build() {
        int size = commitsModel.size();
        CalculateModel<RowOfNode> rowsModel = new PartSaveCalculateModel<RowOfNode>();
        rowsModel.prepare(new RowOfNodeCalculator(commitsModel), size);

        CalculateModel<HideCommits> hideModel = new PartSaveCalculateModel<HideCommits>();
        hideModel.prepare(new HideCommitsCalculator(commitsModel), size);
        return new CommitRowListAdapter(rowsModel, hideModel, commitsModel);
    }



}
