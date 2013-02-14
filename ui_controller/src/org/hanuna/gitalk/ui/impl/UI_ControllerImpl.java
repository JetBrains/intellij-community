package org.hanuna.gitalk.ui.impl;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.data.DataLoader;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.impl.DataLoaderImpl;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.ui.ControllerListener;
import org.hanuna.gitalk.ui.UI_Controller;
import org.hanuna.gitalk.ui.tables.GraphTableModel;
import org.hanuna.gitalk.ui.tables.RefTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class UI_ControllerImpl implements UI_Controller {

    // if something error happened - return null
    @Nullable
    public static UI_ControllerImpl fullLogLoad(final ControllerListener listener) {
        DataLoader dataLoader = new DataLoaderImpl();
        listener.setState(ControllerListener.State.PROGRESS);
        try {
            dataLoader.readAllLog(new Executor<String>() {
                @Override
                public void execute(String key) {
                    listener.setUpdateProgressMessage(key);
                }
            });
            UI_ControllerImpl ui_controller = new UI_ControllerImpl(dataLoader);
            ui_controller.addControllerListener(listener);
            listener.setState(ControllerListener.State.USUAL);
            return ui_controller;
        } catch (IOException e) {
            listener.setState(ControllerListener.State.ERROR);
            listener.setErrorMessage(e.getMessage());
        } catch (GitException e) {
            listener.setState(ControllerListener.State.ERROR);
            listener.setErrorMessage(e.getMessage());
        }
        return null;
    }

    // if something error happened - return null
    @NotNull
    public static UI_ControllerImpl firstPartLogLoad(final ControllerListener listener) {
        DataLoader dataLoader = new DataLoaderImpl();
        listener.setState(ControllerListener.State.PROGRESS);
        try {
            dataLoader.readNextPart(new Executor<String>() {
                @Override
                public void execute(String key) {
                    listener.setUpdateProgressMessage(key);
                }
            });
            UI_ControllerImpl ui_controller = new UI_ControllerImpl(dataLoader);
            ui_controller.addControllerListener(listener);
            listener.setState(ControllerListener.State.USUAL);
            return ui_controller;
        } catch (IOException e) {
            listener.setState(ControllerListener.State.ERROR);
            listener.setErrorMessage(e.getMessage());
        } catch (GitException e) {
            listener.setState(ControllerListener.State.ERROR);
            listener.setErrorMessage(e.getMessage());
        }
        return null;
    }


    private final DataLoader dataLoader;
    private final DataPack dataPack;
    private final EventsController events = new EventsController();
    private final RefTableModel refTableModel;
    private final GraphTableModel graphTableModel;

    private GraphElement prevGraphElement = null;
    private Set<Hash> prevSelectionBranches;

    private UI_ControllerImpl(@NotNull DataLoader dataLoader) {
        this.dataLoader = dataLoader;

        this.dataPack = dataLoader.getDataPack();
        this.refTableModel = new RefTableModel(dataPack.getRefsModel(), dataPack.getCommitDataGetter());
        this.graphTableModel = new GraphTableModel(dataPack);
        this.prevSelectionBranches = new HashSet<Hash>(refTableModel.getCheckedCommits());
    }

    @Override
    @NotNull
    public TableModel getGraphTableModel() {
        return graphTableModel;
    }

    @Override
    @NotNull
    public TableModel getRefsTableModel() {
        return refTableModel;
    }

    @Override
    public void addControllerListener(@NotNull ControllerListener listener) {
        events.addListener(listener);
    }

    @Override
    public void removeAllListeners() {
        events.removeAllListeners();
    }

    @Override
    public void over(@Nullable GraphElement graphElement) {
        SelectController selectController = dataPack.getPrintCellModel().getSelectController();
        FragmentManager fragmentManager = dataPack.getGraphModel().getFragmentManager();
        if (graphElement == prevGraphElement) {
            return;
        } else {
            prevGraphElement = graphElement;
        }
        selectController.deselectAll();
        if (graphElement == null) {
            events.runUpdateUI();
        } else {
            GraphFragment graphFragment = fragmentManager.relateFragment(graphElement);
            selectController.select(graphFragment);
            events.runUpdateUI();
        }
    }

    public void click(@Nullable GraphElement graphElement) {
        SelectController selectController = dataPack.getPrintCellModel().getSelectController();
        FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
        selectController.deselectAll();
        if (graphElement == null) {
            return;
        }
        GraphFragment fragment = fragmentController.relateFragment(graphElement);
        if (fragment == null) {
            return;
        }
        Replace replace;
        if (fragment.isVisible()) {
            replace = fragmentController.hide(fragment);
        } else {
            replace = fragmentController.show(fragment);
        }
        events.runUpdateUI();
        //TODO:
        events.runJumpToRow(replace.from());
    }

    @Override
    public void updateVisibleBranches() {
        Set<Hash> checkedCommitHashes = refTableModel.getCheckedCommits();
        if (! prevSelectionBranches.equals(checkedCommitHashes)) {
            MyTimer timer = new MyTimer("update branch shows");

            prevSelectionBranches = new HashSet<Hash>(checkedCommitHashes);
            dataPack.getGraphModel().setVisibleBranchesNodes(new Get<Node, Boolean>() {
                @NotNull
                @Override
                public Boolean get(@NotNull Node key) {
                    return prevSelectionBranches.contains(key.getCommitHash());
                }
            });

            events.runUpdateUI();
            //TODO:
            events.runJumpToRow(0);

            timer.print();
        }
    }

    @Override
    public void readNextPart() {
        if (dataLoader.allLogReadied()) {
            throw new IllegalStateException("all log read!");
        }
        try {
            events.setState(ControllerListener.State.PROGRESS);
            dataLoader.readNextPart(new Executor<String>() {
                @Override
                public void execute(String key) {
                    events.setUpdateProgressMessage(key);
                }
            });

        } catch (IOException e) {
            events.setState(ControllerListener.State.ERROR);
            events.setErrorMessage(e.getMessage());
        } catch (GitException e) {
            events.setState(ControllerListener.State.ERROR);
            events.setErrorMessage(e.getMessage());
        }
    }


    @Override
    public void hideAll() {
        MyTimer timer = new MyTimer("hide All");
        dataPack.getGraphModel().getFragmentManager().hideAll();

        events.runUpdateUI();
        //TODO:
        events.runJumpToRow(0);
        timer.print();
    }

    @Override
    public void setLongEdgeVisibility(boolean visibility) {
        //TODO:
    }

}
