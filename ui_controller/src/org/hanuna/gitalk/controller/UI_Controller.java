package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.mutable_graph.graph_fragment_controller.GraphFragmentController;
import org.hanuna.gitalk.printmodel.PrintCell;
import org.hanuna.gitalk.printmodel.PrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;

import static org.hanuna.gitalk.controller.EventsController.ControllerListener;

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

    public UI_Controller(Graph graph, RefsModel refsModel) {
        this.fragmentController = graph.getFragmentController();
        this.printCellModel = new PrintCellModel(graph);
        this.selectController = printCellModel.getSelectController();

        this.graphTableModel = new GraphTableModel(graph, refsModel, printCellModel);
        this.refTableModel = new RefTableModel(refsModel);
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

}
