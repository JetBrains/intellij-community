package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListPopup;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

public class SelectInAction extends AnAction {
  
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.select.in");
    SelectInContext context = SelectInContext.createContext(e);
    if (context == null) return;
    invoke(context);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    SelectInContext context = SelectInContext.createContext(event);
    if (context == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    } else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  public void invoke(SelectInContext context) {
    final SelectInTarget[] targetVector = context.getTargets();

    final JList list;
    final Runnable runnable;

    if (targetVector.length > 0) {
      list = new JList(targetVector);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setCellRenderer(new MyListCellRenderer());

      runnable = new MyRunnable(context, list);
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
      context.getProject()
    );
    Point p = context.getPoint();

    listPopup.show(p.x, p.y);
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
      SelectInManager selectInManager = myContext.getManager();
      selectInManager.moveToTop(selected);
      myContext.selectIn(selected);
    }
  }
}