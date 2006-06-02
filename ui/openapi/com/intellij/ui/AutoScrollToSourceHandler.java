package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.OpenSourceUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class AutoScrollToSourceHandler {
  private Alarm myAutoScrollAlarm;

  protected AutoScrollToSourceHandler() {
  }

  public void install(final JTree tree) {
    myAutoScrollAlarm = new Alarm();
    tree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) return;

        TreePath location = tree.getPathForLocation(e.getPoint().x, e.getPoint().y);
        if (location != null) {
          onMouseClicked(tree);
        }
      }
    });
    tree.addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          onSelectionChanged(tree);
        }
      }
    );
  }

  public void install(final JList jList) {
    myAutoScrollAlarm = new Alarm();
    jList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) return;
        final Object source = e.getSource();
        final int index = jList.locationToIndex(SwingUtilities.convertPoint(source instanceof Component ? (Component)source : null, e.getPoint(), jList));
        if (index >= 0 && index < jList.getModel().getSize()) {
          onMouseClicked(jList);
        }
      }
    });
    jList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        onSelectionChanged(jList);
      }
    });
  }

  private void onMouseClicked(final Component component) {
    myAutoScrollAlarm.cancelAllRequests();
    if (isAutoScrollMode()){
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          scrollToSource(component);
        }
      });
    }
  }

  private void onSelectionChanged(final Component component) {
    if (!isAutoScrollMode()) {
      return;
    }
    if (needToCheckFocus() && !component.hasFocus()) {
      return;
    }
    myAutoScrollAlarm.cancelAllRequests();
    myAutoScrollAlarm.addRequest(
      new Runnable() {
        public void run() {
          scrollToSource(component);
        }
      },
      500
    );
  }

  protected boolean needToCheckFocus(){
    return true;
  }

  protected abstract boolean isAutoScrollMode();
  protected abstract void setAutoScrollMode(boolean state);

  protected void scrollToSource(Component tree) {
    DataContext dataContext=DataManager.getInstance().getDataContext(tree);
    final VirtualFile vFile = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (vFile != null) {
      // Attempt to navigate to the virtual file with unknown file type will show a modal dialog
      // asking to register some file type for this file. This behaviour is undesirable when autoscrolling.
      if (FileTypeManager.getInstance().getFileTypeByFile(vFile) == StdFileTypes.UNKNOWN) return;
    }
    if (dataContext.getData(DataConstants.MODULE_CONTEXT) != null) {
      // we are not going to open module properties dialog during autoscrolling
      return;
    }
    OpenSourceUtil.openSourcesFrom(dataContext, false);
  }

  public ToggleAction createToggleAction() {
    return new ToggleAction(UIBundle.message("autoscroll.to.source.action.name"),
                            UIBundle.message("autoscroll.to.source.action.description"), IconLoader.getIcon("/general/autoscrollToSource.png")) {
      public boolean isSelected(AnActionEvent event) {
        return isAutoScrollMode();
      }

      public void setSelected(AnActionEvent event, boolean flag) {
        setAutoScrollMode(flag);
      }
    };
  }
}

