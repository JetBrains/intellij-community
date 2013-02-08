package org.hanuna.gitalk.ui_controller;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.graphmodel.impl.GraphModelImpl;
import org.hanuna.gitalk.log.commit.Commit;
import org.hanuna.gitalk.log.commit.Hash;
import org.hanuna.gitalk.log.commitdata.CommitDataGetter;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.printmodel.impl.GraphPrintCellModelImpl;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * @author erokhins
 */
public class DataPack {
    private final RefsModel refsModel;
    private final List<Commit> commits;
    private final CommitDataGetter commitDataGetter;
    private MutableGraph graph;
    private GraphModel graphModel;
    private GraphPrintCellModel printCellModel;

    public DataPack(final RefsModel refsModel, List<Commit> commits, CommitDataGetter commitDataGetter) {
        this.refsModel = refsModel;
        this.commits = commits;
        this.commitDataGetter = commitDataGetter;
        final MyTimer graphTimer = new MyTimer("graph build");

        final Set<Hash> trackedHashes = new HashSet<Hash>();
        for (Commit commit : refsModel.getOrderedLogTrackedCommit()) {
            trackedHashes.add(commit.getCommitHash());
        }

        graph = GraphBuilder.build(commits);
        graphModel = new GraphModelImpl(graph);
        graphModel.getFragmentManager().setUnhiddenNodes(new Get<Node, Boolean>() {
            @NotNull
            @Override
            public Boolean get(@NotNull Node key) {
                return key.getDownEdges().size() == 0 || trackedHashes.contains(key.getCommitHash());
            }
        });

        graphTimer.print();

        updatePrintModel();
    }

    public void setShowBranches(@NotNull final Set<Hash> startedCommit) {
        graphModel.setVisibleBranchesNodes(new Get<Node, Boolean>() {
            @NotNull
            @Override
            public Boolean get(@NotNull Node key) {
                return startedCommit.contains(key.getCommitHash());
            }
        });
    }

    @NotNull
    public RefsModel getRefsModel() {
        return refsModel;
    }

    @NotNull
    public Graph getGraph() {
        return graph;
    }

    @NotNull
    public GraphPrintCellModel getPrintCellModel() {
        return printCellModel;
    }

    @NotNull
    public FragmentManager getFragmentManager() {
        return graphModel.getFragmentManager();
    }

    @NotNull
    public SelectController getSelectController() {
        return printCellModel.getSelectController();
    }

    public CommitDataGetter getCommitDataGetter() {
        return commitDataGetter;
    }

    public void hideAll() {
        graphModel.getFragmentManager().hideAll();
    }

    public void updatePrintModel() {
        MyTimer printModelTimer = new MyTimer("print model build");
        printCellModel = new GraphPrintCellModelImpl(graph);
        printModelTimer.print();

        graphModel.removeAllListeners();
        graphModel.addUpdateListener(new Executor<Replace>() {
            @Override
            public void execute(Replace key) {
                printCellModel.recalculate(key);
            }
        });
    }
}
