package org.hanuna.gitalk.ui.tables;

import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.ui.impl.DateConverter;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;

/**
* @author erokhins
*/
public class GraphTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Subject", "Author", "Date"};
    private final DataPack dataPack;

    public GraphTableModel(@NotNull DataPack dataPack) {
        this.dataPack = dataPack;
    }

    @Override
    public int getRowCount() {
        return dataPack.getGraphModel().getGraph().getNodeRows().size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Node commitNode = dataPack.getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
        CommitData data;
        if (commitNode == null) {
            data = null;
        } else {
            data = dataPack.getCommitDataGetter().getCommitData(commitNode);
        }
        switch (columnIndex) {
            case 0:
                String message = "";
                List<Ref> refs = Collections.emptyList();
                if (data != null) {
                    message = data.getMessage();
                    refs = dataPack.getRefsModel().refsToCommit(data.getCommitHash());
                }
                return new GraphCommitCell(dataPack.getPrintCellModel().getGraphPrintCell(rowIndex), message, refs);
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
