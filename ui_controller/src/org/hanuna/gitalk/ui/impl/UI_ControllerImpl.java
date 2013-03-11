package org.hanuna.gitalk.ui.impl;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
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

    private final DataLoader dataLoader = new DataLoaderImpl();
    private final EventsController events = new EventsController();

    private DataPack dataPack;
    private RefTableModel refTableModel;
    private GraphTableModel graphTableModel;

    private GraphElement prevGraphElement = null;
    private Set<Hash> prevSelectionBranches;

    private void dataInit() {
        dataPack = dataLoader.getDataPack();
        refTableModel = new RefTableModel(dataPack.getRefsModel(), dataPack.getCommitDataGetter());
        graphTableModel = new GraphTableModel(dataPack);

        prevSelectionBranches = new HashSet<Hash>(refTableModel.getCheckedCommits());
    }

    public void init(boolean readAllLog) {
        events.setState(ControllerListener.State.PROGRESS);
        Executor<String> statusUpdater = new Executor<String>() {
            @Override
            public void execute(String key) {
                events.setUpdateProgressMessage(key);
            }
        };

        try {
            if (readAllLog) {
                dataLoader.readAllLog(statusUpdater);
            } else {
                dataLoader.readNextPart(statusUpdater);
            }
            dataInit();
            events.setState(ControllerListener.State.USUAL);
        } catch (IOException e) {
            events.setState(ControllerListener.State.ERROR);
            events.setErrorMessage(e.getMessage());
        } catch (GitException e) {
            events.setState(ControllerListener.State.ERROR);
            events.setErrorMessage(e.getMessage());
        }
    }


    public DataPack getDataPack() {
        return dataPack;
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
        UpdateRequest updateRequest = fragmentController.changeVisibility(fragment);
        events.runUpdateUI();
        //TODO:
        events.runJumpToRow(updateRequest.from());
    }

    @Override
    public void doubleClick(int rowIndex) {
        if (rowIndex == graphTableModel.getRowCount() - 1) {
            readNextPart();
        }
    }

    @Override
    public void updateVisibleBranches() {
        final Set<Hash> checkedCommitHashes = refTableModel.getCheckedCommits();
        if (! prevSelectionBranches.equals(checkedCommitHashes)) {
            MyTimer timer = new MyTimer("update branch shows");

            prevSelectionBranches = new HashSet<Hash>(checkedCommitHashes);
            dataPack.getGraphModel().setVisibleBranchesNodes(new Function<Node, Boolean>() {
                @NotNull
                @Override
                public Boolean get(@NotNull Node key) {
                    return checkedCommitHashes.contains(key.getCommitHash());
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    events.setState(ControllerListener.State.PROGRESS);

                    dataLoader.readNextPart(new Executor<String>() {
                        @Override
                        public void execute(String key) {
                            events.setUpdateProgressMessage(key);
                        }
                    });


                    events.setState(ControllerListener.State.USUAL);
                    events.runUpdateUI();

                } catch (IOException e) {
                    events.setState(ControllerListener.State.ERROR);
                    events.setErrorMessage(e.getMessage());
                } catch (GitException e) {
                    events.setState(ControllerListener.State.ERROR);
                    events.setErrorMessage(e.getMessage());
                }
            }
        }).start();

    }

    @Override
    public void showAll() {
        dataPack.getGraphModel().getFragmentManager().showAll();
        events.runUpdateUI();
        events.runJumpToRow(0);
    }

    @Override
    public void hideAll() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                events.setState(ControllerListener.State.PROGRESS);
                events.setUpdateProgressMessage("Hide long branches");
                MyTimer timer = new MyTimer("hide All");
                dataPack.getGraphModel().getFragmentManager().hideAll();

                events.runUpdateUI();
                //TODO:
                events.runJumpToRow(0);
                timer.print();

                events.setState(ControllerListener.State.USUAL);
            }
        }).start();
    }

    @Override
    public void setLongEdgeVisibility(boolean visibility) {
        dataPack.getPrintCellModel().setLongEdgeVisibility(visibility);
        events.runUpdateUI();
    }

}
