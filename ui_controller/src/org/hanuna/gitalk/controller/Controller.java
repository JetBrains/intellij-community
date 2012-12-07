package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.common.Interval;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.controller.branchvisibility.HideShowBranch;
import org.hanuna.gitalk.controller.branchvisibility.NodeInterval;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.printmodel.PrintCellRow;
import org.hanuna.gitalk.printmodel.SpecialCell;
import org.hanuna.gitalk.printmodel.cells.Cell;
import org.hanuna.gitalk.printmodel.cells.LayoutModel;
import org.hanuna.gitalk.printmodel.cells.NodeCell;
import org.hanuna.gitalk.printmodel.cells.builder.LayoutModelBuilder;
import org.hanuna.gitalk.printmodel.cells.builder.PrintCellRowModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

/**
 * @author erokhins
 */
public class Controller {
    private final Graph graph;
    private final SelectController selectController;
    private final HideShowBranch hideShowBranch;

    public LayoutModel getLayoutModel() {
        return layoutModel;
    }

    private LayoutModel layoutModel;
    private PrintCellRowModel printCellRowModel;

    public Controller(Graph graph) {
        this.graph = graph;
        this.selectController = new SelectController();
        this.hideShowBranch = new HideShowBranch();
    }

    public void prepare() {
        LayoutModelBuilder layoutModelBuilder = new LayoutModelBuilder(graph);
        layoutModel = layoutModelBuilder.build();
        printCellRowModel = new PrintCellRowModel(layoutModel);
    }

    public TableModel getTableModel() {
        return new GitAlkTableModel();
    }

    @Nullable
    public Commit getCommitInRow(int rowIndex) {
        ReadOnlyList<SpecialCell> cells = printCellRowModel.getPrintCellRow(rowIndex).getSpecialCell();
        for (SpecialCell cell : cells) {
            if (cell.getType() == SpecialCell.Type.commitNode) {
                assert NodeCell.class == cell.getCell().getClass();
                Node node =  ((NodeCell) cell.getCell()).getNode();
                return node.getCommit();
            }
        }
        return null;
    }

    public PrintCellRow getPrintRow(int rowIndex) {
        return printCellRowModel.getPrintCellRow(rowIndex);
    }

    public void over(@Nullable Cell cell) {
        selectController.clearSelect();
        Edge edge = hideShowBranch.hideBranchOver(cell);
        if (edge != null) {
            selectController.selectEdge(edge);
            return;
        }
        NodeInterval nodeInterval = hideShowBranch.branchInterval(cell);
        if (nodeInterval != null) {
            selectController.selectNodeInterval(nodeInterval);
        }
    }

    public int click(@Nullable Cell cell) {
        Edge edge = hideShowBranch.hideBranchOver(cell);
        if (edge != null) {
            selectController.clearSelect();
            Node up = edge.getUpNode();
            Node down = edge.getDownNode();
            Interval old = new Interval(up.getRowIndex(), down.getRowIndex());
            graph.showBranch(edge);
            Interval upd = new Interval(up.getRowIndex(), down.getRowIndex());
            layoutModel.update(Replace.buildFromChangeInterval(old.from(), old.to(), upd.from(), upd.to()));
            return upd.from();
        }
        NodeInterval nodeInterval = hideShowBranch.branchInterval(cell);
        if (nodeInterval != null) {
            selectController.clearSelect();
            Interval old = new Interval(nodeInterval.getUp().getRowIndex(), nodeInterval.getDown().getRowIndex());
            graph.hideBranch(nodeInterval.getUp(), nodeInterval.getDown());
            Interval upd = new Interval(nodeInterval.getUp().getRowIndex(), nodeInterval.getDown().getRowIndex());
            layoutModel.update(Replace.buildFromChangeInterval(old.from(), old.to(), upd.from(), upd.to()));
            return upd.from();
        }
        return -1;
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
                    if (data != null) {
                        message = data.getMessage();
                    }
                    return new GraphTableCell(printCellRowModel.getPrintCellRow(rowIndex), message);
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
