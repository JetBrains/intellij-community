package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListPopup;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class SelectInAction extends AnAction {
  
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.select.in");
    SelectInContextImpl.SelectInContextProvider context = SelectInContextImpl.createContext(e);
    if (context == null) return;
    invoke(context);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (SelectInContextImpl.createContext(event) == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    } else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  public void invoke(SelectInContextImpl.SelectInContextProvider contextProvider) {
    final SelectInTarget[] targetVector = getTargets(contextProvider.getContext());

    final JList list;
    final Runnable runnable;

    if (targetVector.length > 0) {
      list = new JList(targetVector);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setCellRenderer(new MyListCellRenderer());

      runnable = new MyRunnable(contextProvider.getContext(), list);
    }
    else {
      list = new JList(new String[] {"No targets available in this context"});
      //list.setSelectionModel(ListSelectionModel.SINGLE_SELECTION);

      runnable = new Runnable() {
        public void run() {
          // empty runnable
        }
      };
    }
    ListPopup listPopup = new ListPopup(
      " Select Target ",
      list,
      runnable,
      contextProvider.getContext().getProject()
    );
    Point p = contextProvider.getInvocationPoint();

    listPopup.show(p.x, p.y);
  }

  protected SelectInTarget[] getTargets(SelectInContext context) {
     ArrayList<SelectInTarget> result = getTargetsFor(context);

    if (result.size() > 1) {
      rearrangeTargetList(context, result);
    }
    return result.toArray(new SelectInTarget[result.size()]);
  }

  private ArrayList<SelectInTarget> getTargetsFor(final SelectInContext context) {
    ArrayList<SelectInTarget> result = new ArrayList<SelectInTarget>();
    final SelectInTarget[] targets = getSelectInManager(context.getProject()).getTargets();
    for (int i = 0; i < targets.length; i++) {
      SelectInTarget target = targets[i];
      if (target.canSelect(context)) {
        result.add(target);
      }
    }
    return result;
  }

  private static SelectInManager getSelectInManager(Project project) {
    return SelectInManager.getInstance(project);
  }

  private void rearrangeTargetList(final SelectInContext context, final ArrayList<SelectInTarget> result) {
    final String activeToolWindowId = ToolWindowManager.getInstance(context.getProject()).getActiveToolWindowId();
    if (activeToolWindowId != null) {
      SelectInTarget firstTarget = result.get(0);
      if (activeToolWindowId.equals(firstTarget.getToolWindowId())) {
        boolean shouldMoveToBottom = true;
        if (ToolWindowId.PROJECT_VIEW.equals(activeToolWindowId)) {
          final String currentMinorViewId = ProjectView.getInstance(context.getProject()).getCurrentViewId();
          shouldMoveToBottom = (currentMinorViewId != null) && currentMinorViewId.equals(firstTarget.getMinorViewId());
        }
        if (shouldMoveToBottom) {
          result.remove(0);
          result.add(firstTarget);
        }
      }
    }
  }

  private static final class MyListCellRenderer extends ColoredListCellRenderer{
    private final SimpleTextAttributes myAttributes;

    public MyListCellRenderer(){
      myAttributes=SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    }

    protected void customizeCellRenderer(
      JList list,
      Object value,
      int index,
      boolean selected,
      boolean hasFocus
    ){
      append(value.toString(),myAttributes);
    }
  }

  private static class MyRunnable implements Runnable {
    private final JList myList;
    private final SelectInContext myContext;

    public MyRunnable(SelectInContext context, JList list) {
      myContext = context;
      myList = list;
    }

    public void run() {
      SelectInTarget selected = (SelectInTarget)myList.getSelectedValue();
      if (selected == null) {
        // this can be if you click item with Ctrl pressed
        return;
      }
      SelectInManager selectInManager = getSelectInManager(myContext.getProject());
      selectInManager.moveToTop(selected);
      selected.selectIn(myContext, true);
    }
  }
}