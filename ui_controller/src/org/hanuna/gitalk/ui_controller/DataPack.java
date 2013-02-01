package org.hanuna.gitalk.ui_controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.new_mutable.GraphAdapter;
import org.hanuna.gitalk.graph.new_mutable.GraphBuilder;
import org.hanuna.gitalk.graph.new_mutable.MutableGraph;
import org.hanuna.gitalk.log.commit.CommitDataGetter;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.printmodel.impl.GraphPrintCellModelImpl;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
    private MutableGraph newGraph;
    private GraphAdapter graph;
    private GraphPrintCellModel printCellModel;

    public DataPack(RefsModel refsModel, List<Commit> commits, CommitDataGetter commitDataGetter) {
        this.refsModel = refsModel;
        this.commits = commits;
        this.commitDataGetter = commitDataGetter;
        MyTimer graphTimer = new MyTimer("graph build");
        //graph = GraphBuilder.build(commits, refsModel);
        newGraph = GraphBuilder.build(commits);
        newGraph.getVisibilityController().setVisibleCommits(commits);
        graph = new GraphAdapter(newGraph, refsModel.getOrderedLogTrackedCommit());
        graphTimer.print();

        updatePrintModel();
    }

    public void setShowBranches(@NotNull Set<Commit> startedCommit) {
        Set<Commit> notAddedVisibleCommits = new HashSet<Commit>();
        List<Commit> showCommits = new ArrayList<Commit>();

        for (Commit commit : commits) {
            if (startedCommit.contains(commit) || notAddedVisibleCommits.contains(commit)) {
                showCommits.add(commit);
                notAddedVisibleCommits.remove(commit);
                List<Commit> parents = commit.getParents();
                if (parents != null) {
                    for (Commit parent : parents) {
                        notAddedVisibleCommits.add(parent);
                    }
                }
            }
        }
        newGraph.getVisibilityController().setVisibleCommits(showCommits);
        newGraph.updateVisibleRows();
        //graph = GraphBuilder.build(Collections.unmodifiableList(showCommits), refsModel);
        updatePrintModel();
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
    public GraphFragmentController getFragmentController() {
        return graph.getFragmentController();
    }

    @NotNull
    public SelectController getSelectController() {
        return printCellModel.getSelectController();
    }

    public CommitDataGetter getCommitDataGetter() {
        return commitDataGetter;
    }

    public void hideAll() {
        graph.removeAllListeners();
        graph.getFragmentManager().hideAll();
        updatePrintModel();
    }

    public void updatePrintModel() {
        MyTimer printModelTimer = new MyTimer("print model build");
        printCellModel = new GraphPrintCellModelImpl(graph);
        printModelTimer.print();

        graph.removeAllListeners();
        graph.addUpdateListener(new Graph.GraphUpdateListener() {
            @Override
            public void doReplace(@NotNull Replace replace) {
                printCellModel.recalculate(replace);
            }
        });
    }
}
