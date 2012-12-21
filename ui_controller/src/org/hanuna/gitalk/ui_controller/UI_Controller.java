package org.hanuna.gitalk.ui_controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.controller.Controller;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.printmodel.impl.GraphPrintCellModelImpl;
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
    private final Controller controller;
    private final EventsController events = new EventsController();
    private final RefTableModel refTableModel;

    private GraphPrintCellModel graphPrintCellModel;
    private GraphTableModel graphTableModel;

    private GraphElement prevGraphElement = null;
    private Set<Commit> prevSelectionBranches;

    public UI_Controller(Controller controller) {
        this.controller = controller;
        Graph graph = controller.getGraph();
        RefsModel refsModel = controller.getRefsModel();

        this.refTableModel = new RefTableModel(refsModel);

        this.graphPrintCellModel = new GraphPrintCellModelImpl(graph);
        this.graphTableModel = new GraphTableModel(graph, refsModel, graphPrintCellModel);

        this.prevSelectionBranches = new HashSet<Commit>(refTableModel.getCheckedCommits());
    }

    public void updateGraph() {
        Graph graph = controller.getGraph();
        this.graphPrintCellModel = new GraphPrintCellModelImpl(graph);
        graphTableModel.rewriteGraph(graph, graphPrintCellModel);
    }

    public TableModel getGraphTableModel() {
        return graphTableModel;
    }

    public RefTableModel getRefTableModel() {
        return refTableModel;
    }

    public GraphPrintCell getGraphPrintCell(int rowIndex) {
        return graphPrintCellModel.getGraphPrintCell(rowIndex);
    }

    public void addControllerListener(@NotNull ControllerListener listener) {
        events.addListener(listener);
    }

    public void over(@Nullable GraphElement graphElement) {
        SelectController selectController = graphPrintCellModel.getSelectController();
        GraphFragmentController fragmentController = controller.getGraph().getFragmentController();
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
        SelectController selectController = graphPrintCellModel.getSelectController();
        GraphFragmentController fragmentController = controller.getGraph().getFragmentController();
        selectController.deselectAll();
        if (graphElement == null) {
            return;
        }
        GraphFragment fragment = fragmentController.relateFragment(graphElement);
        if (fragment == null) {
            return;
        }
        boolean visible = fragmentController.isVisible(fragment);
        Replace replace = fragmentController.setVisible(fragment, !visible);
        graphPrintCellModel.recalculate(replace);
        events.runUpdateTable();
        events.runJumpToRow(replace.from());
    }

    public void runUpdateShowBranches() {
        Set<Commit> checkedCommits = refTableModel.getCheckedCommits();
        if (! prevSelectionBranches.equals(checkedCommits)) {
            Timer timer = new Timer("update branch shows");

            prevSelectionBranches = new HashSet<Commit>(checkedCommits);
            controller.setShowBranches(checkedCommits);
            updateGraph();
            events.runUpdateTable();
            events.runJumpToRow(0);

            timer.print();
        }
    }


}
