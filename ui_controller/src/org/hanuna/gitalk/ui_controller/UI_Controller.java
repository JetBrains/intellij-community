package org.hanuna.gitalk.ui_controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.mutable_graph.graph_fragment_controller.GraphFragmentController;
import org.hanuna.gitalk.printmodel.PrintCell;
import org.hanuna.gitalk.printmodel.PrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.refs.RefsModel;
import org.hanuna.gitalk.ui_controller.table_models.GraphTableModel;
import org.hanuna.gitalk.ui_controller.table_models.RefTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import java.util.HashSet;
import java.util.Set;

import static org.hanuna.gitalk.ui_controller.EventsController.ControllerListener;

/**
 * @author erokhins
 */
public class UI_Controller {
    private final SelectController selectController;
    private final PrintCellModel printCellModel;
    private final GraphFragmentController fragmentController;
    private final EventsController events = new EventsController();
    private final GraphTableModel graphTableModel;
    private final RefTableModel refTableModel;

    private GraphElement prevGraphElement = null;
    private Set<Commit> prevSelectionBranches;

    public UI_Controller(Graph graph, RefsModel refsModel) {
        this.fragmentController = graph.getFragmentController();
        this.printCellModel = new PrintCellModel(graph);
        this.selectController = printCellModel.getSelectController();

        this.graphTableModel = new GraphTableModel(graph, refsModel, printCellModel);
        this.refTableModel = new RefTableModel(refsModel);
        this.prevSelectionBranches = new HashSet<Commit>(refTableModel.getCheckedCommits());
    }

    public TableModel getGraphTableModel() {
        return graphTableModel;
    }

    public RefTableModel getRefTableModel() {
        return refTableModel;
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
        } else {
            prevGraphElement = graphElement;
        }
        selectController.deselectAll();
        if (graphElement == null) {
            events.runUpdateTable();
        } else {
            GraphFragment graphFragment = fragmentController.relateFragment(graphElement);
            selectController.select(graphFragment);
            events.runUpdateTable();
        }
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

    public void runUpdateShowBranches() {
        Set<Commit> checkedCommits = refTableModel.getCheckedCommits();
        if (! prevSelectionBranches.equals(checkedCommits)) {
            prevSelectionBranches = new HashSet<Commit>(checkedCommits);
        }
    }


}
