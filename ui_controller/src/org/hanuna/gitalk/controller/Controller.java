package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.mutable_graph.graph_fragment_controller.GraphFragmentController;
import org.hanuna.gitalk.printmodel.PrintCell;
import org.hanuna.gitalk.printmodel.PrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.printmodel.SpecialCell;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import static org.hanuna.gitalk.controller.EventsController.ControllerListener;

/**
 * @author erokhins
 */
public class Controller {
    private final Graph graph;
    private final SelectController selectController;
    private final PrintCellModel printCellModel;
    private final GraphFragmentController fragmentController;
    private final RefsModel refsModel;
    private final EventsController events = new EventsController();

    private GraphElement prevGraphElement = null;

    public Controller(Graph graph, RefsModel refsModel) {
        this.graph = graph;
        this.fragmentController = graph.getFragmentController();
        this.refsModel = refsModel;
        this.selectController = new SelectController();
        this.printCellModel = new PrintCellModel(graph);
    }

    public TableModel getTableModel() {
        return new GitAlkTableModel();
    }

    @Nullable
    private Commit getCommitInRow(int rowIndex) {
        ReadOnlyList<SpecialCell> cells = printCellModel.getPrintCellRow(rowIndex).getSpecialCell();
        for (SpecialCell cell : cells) {
            if (cell.getType() == SpecialCell.Type.COMMIT_NODE) {
                GraphElement element = cell.getGraphElement();
                Node node =  element.getNode();
                assert node != null;
                return node.getCommit();
            }
        }
        return null;
    }

    public PrintCell getGraphPrintCell(int rowIndex) {
        return printCellModel.getPrintCellRow(rowIndex);
    }

    public void addControllerListener(@NotNull ControllerListener listener) {
        events.addListener(listener);
    }

    public void over(@Nullable GraphElement graphElement) {
        if (graphElement == prevGraphElement) {
            return;
        }
        selectController.deselectAll();
        if (graphElement == null) {
            return;
        }
        GraphFragment graphFragment = fragmentController.relateFragment(graphElement);
        selectController.select(graphFragment);
        events.runUpdateTable();
        prevGraphElement = graphElement;
    }

    public void click(@Nullable GraphElement graphElement) {
        selectController.deselectAll();
        if (graphElement == null) {
            return;
        }
        GraphFragment fragment = fragmentController.relateFragment(graphElement);
        if (fragment == null) {
            return;
        }
        Replace replace;
        if (fragmentController.isHidden(fragment)) {
            replace = fragmentController.showFragment(fragment);
        } else {
            replace = fragmentController.hideFragment(fragment);
        }
        printCellModel.recalculate(replace);
        events.runUpdateTable();
        events.runJumpToRow(replace.from());
    }

    private class GitAlkTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Subject", "Author", "Date"};

        @Override
        public int getRowCount() {
            return graph.getNodeRows().size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Commit commit = getCommitInRow(rowIndex);
            CommitData data;
            if (commit == null) {
                data = null;
            } else {
                data = commit.getData();
                assert data != null;
            }
            switch (columnIndex) {
                case 0:
                    String message = "";
                    ReadOnlyList<Ref> refs = ReadOnlyList.emptyList();
                    if (data != null) {
                        message = data.getMessage();
                        refs = refsModel.refsToCommit(commit.hash());
                    }
                    return new GraphTableCell(printCellModel.getPrintCellRow(rowIndex), message, refs);
                case 1:
                    if (data == null) {
                        return "";
                    } else {
                        return data.getAuthor();
                    }
                case 2:
                    if (data == null) {
                        return "";
                    } else {
                        return DateConverter.getStringOfDate(data.getTimeStamp());
                    }
                default:
                    throw new IllegalArgumentException("columnIndex > 2");
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 0:
                    return GraphTableCell.class;
                case 1:
                    return String.class;
                case 2:
                    return String.class;
                default:
                    throw new IllegalArgumentException("column > 2");
            }
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
    }
}
