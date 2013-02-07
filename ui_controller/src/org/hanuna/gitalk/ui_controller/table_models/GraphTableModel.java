package org.hanuna.gitalk.ui_controller.table_models;

import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.Hash;
import org.hanuna.gitalk.log.commitdata.CommitData;
import org.hanuna.gitalk.log.commitdata.CommitDataGetter;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.hanuna.gitalk.ui_controller.DataPack;
import org.hanuna.gitalk.ui_controller.DateConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
* @author erokhins
*/
public class GraphTableModel extends AbstractTableModel {
    private final int COUNT_PRELOAD = 100;
    private final String[] columnNames = {"Subject", "Author", "Date"};
    private final RefsModel refsModel;
    private final CommitDataGetter commitDataGetter;
    private Graph graph;
    private GraphPrintCellModel graphPrintCellModel;

    public GraphTableModel(@NotNull DataPack dataPack) {
        this.graph = dataPack.getGraph();
        this.refsModel = dataPack.getRefsModel();
        this.graphPrintCellModel = dataPack.getPrintCellModel();
        this.commitDataGetter = dataPack.getCommitDataGetter();
    }

    public void rewriteData(@NotNull DataPack dataPack) {
        this.graph = dataPack.getGraph();
        this.graphPrintCellModel = dataPack.getPrintCellModel();
    }

    @Nullable
    private Hash getCommitHashInRow(int rowIndex) {
        List<SpecialPrintElement> printElements = graphPrintCellModel.getGraphPrintCell(rowIndex).getSpecialPrintElements();
        for (SpecialPrintElement printElement : printElements) {
            if (printElement.getType() == SpecialPrintElement.Type.COMMIT_NODE) {
                GraphElement element = printElement.getGraphElement();
                Node node =  element.getNode();
                assert node != null;
                return node.getCommitHash();
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

    private void runPreLoadCommitData(int rowIndex) {
        int c = rowIndex - COUNT_PRELOAD / 2;
        if (c < 0) {
            c = 0;
        }
        List<Hash> commitHashes = new ArrayList<Hash>(COUNT_PRELOAD + 1);
        for (int i = c; i < COUNT_PRELOAD + c && i < getRowCount(); i++) {
            Hash hash = getCommitHashInRow(i);
            if (hash != null) {
                commitHashes.add(hash);
            }
        }
        commitDataGetter.preLoadCommitData(commitHashes);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Hash commitHash = getCommitHashInRow(rowIndex);
        CommitData data;
        if (commitHash == null) {
            data = null;
        } else {
            if (!commitDataGetter.wasLoadData(commitHash)) {
                runPreLoadCommitData(rowIndex);
            }
            data = commitDataGetter.getCommitData(commitHash);
        }
        switch (columnIndex) {
            case 0:
                String message = "";
                List<Ref> refs = Collections.emptyList();
                if (data != null) {
                    message = data.getMessage();
                    refs = refsModel.refsToCommit(commitHash);
                }
                return new GraphCommitCell(graphPrintCellModel.getGraphPrintCell(rowIndex), message, refs);
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
