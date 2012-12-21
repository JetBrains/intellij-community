package org.hanuna.gitalk.ui_controller.table_models;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.printmodel.PrintCellModel;
import org.hanuna.gitalk.printmodel.SpecialCell;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.hanuna.gitalk.ui_controller.DateConverter;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;

/**
* @author erokhins
*/
public class GraphTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Subject", "Author", "Date"};
    private final RefsModel refsModel;
    private Graph graph;
    private PrintCellModel printCellModel;

    public GraphTableModel(Graph graph, RefsModel refsModel, PrintCellModel printCellModel) {
        this.graph = graph;
        this.refsModel = refsModel;
        this.printCellModel = printCellModel;
    }

    public void rewriteGraph(Graph graph, PrintCellModel printCellModel) {
        this.graph = graph;
        this.printCellModel = printCellModel;
    }

    @Nullable
    private Commit getCommitInRow(int rowIndex) {
        List<SpecialCell> cells = printCellModel.getPrintCellRow(rowIndex).getSpecialCell();
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
                List<Ref> refs = Collections.emptyList();
                if (data != null) {
                    message = data.getMessage();
                    refs = refsModel.refsToCommit(commit.hash());
                }
                return new GraphCommitCell(printCellModel.getPrintCellRow(rowIndex), message, refs);
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
                return GraphCommitCell.class;
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
