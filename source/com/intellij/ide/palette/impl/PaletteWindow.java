package com.intellij.ide.palette.impl;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * @author yole
 */
public class PaletteWindow extends JPanel implements DataProvider {
  private Project myProject;
  private ArrayList<PaletteGroupHeader> myGroupHeaders = new ArrayList<PaletteGroupHeader>();
  private PaletteItemProvider[] myProviders;
  private PaletteWindow.MyPropertyChangeListener myPropertyChangeListener = new MyPropertyChangeListener();
  private Set<PaletteGroup> myGroups = new HashSet<PaletteGroup>();
  private JTabbedPane myTabbedPane = new JTabbedPane();
  private JScrollPane myScrollPane = new JScrollPane();
  private MyListSelectionListener myListSelectionListener = new MyListSelectionListener();
  private PaletteGroupHeader myLastFocusedGroup;

  public PaletteWindow(Project project) {
    myProject = project;
    myProviders = project.getComponents(PaletteItemProvider.class);
    for(PaletteItemProvider provider: myProviders) {
      provider.addListener(myPropertyChangeListener);
    }

    setLayout(new GridLayout(1, 1));
    myScrollPane.addMouseListener(new MyScrollPanePopupHandler());
    KeyStroke escStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    new ClearActiveItemAction().registerCustomShortcutSet(new CustomShortcutSet(escStroke), myScrollPane);
    refreshPalette();
  }

  public void refreshPalette() {
    refreshPalette(null);
  }

  public void refreshPalette(@Nullable VirtualFile selectedFile) {
    for(PaletteGroupHeader groupHeader: myGroupHeaders) {
      groupHeader.getComponentList().removeListSelectionListener(myListSelectionListener);
    }
    String[] oldTabNames = collectTabNames(myGroups);
    myTabbedPane.removeAll();
    myGroupHeaders.clear();
    myGroups.clear();

    final ArrayList<PaletteGroup> currentGroups = collectCurrentGroups(selectedFile);
    String[] tabNames = collectTabNames(currentGroups);
    if (tabNames.length == 1) {
      if (oldTabNames.length != 1) {
        remove(myTabbedPane);
        add(myScrollPane);
      }

      PaletteContentWindow contentWindow = new PaletteContentWindow();
      myScrollPane.getViewport().setView(contentWindow);

      for(PaletteGroup group: currentGroups) {
        addGroupToControl(group, contentWindow);
      }

      final JComponent view = (JComponent)myScrollPane.getViewport().getView();
      if (view != null) {
        view.revalidate();
        for(Component component: view.getComponents()) {
          ((JComponent) component).revalidate();
        }
      }
    }
    else {
      if (oldTabNames.length <= 1) {
        remove(myScrollPane);
        add(myTabbedPane);
      }
      for(String tabName: tabNames) {
        PaletteContentWindow contentWindow = new PaletteContentWindow();
        JScrollPane scrollPane = new JScrollPane(contentWindow);
        scrollPane.addMouseListener(new MyScrollPanePopupHandler());
        myTabbedPane.add(tabName, scrollPane);
        for(PaletteGroup group: currentGroups) {
          if (group.getTabName().equals(tabName)) {
            addGroupToControl(group, contentWindow);
          }
        }
      }
      myTabbedPane.revalidate();
    }
  }

  private void addGroupToControl(PaletteGroup group, JComponent control) {
    PaletteGroupHeader groupHeader = new PaletteGroupHeader(this, group);
    myGroupHeaders.add(groupHeader);
    myGroups.add(group);
    control.add(groupHeader);
    PaletteComponentList componentList = new PaletteComponentList(myProject, group);
    control.add(componentList);
    groupHeader.setComponentList(componentList);
    componentList.addListSelectionListener(myListSelectionListener);
  }

  private static String[] collectTabNames(final Collection<PaletteGroup> groups) {
    Set<String> result = new TreeSet<String>();
    for(PaletteGroup group: groups) {
      result.add(group.getTabName());
    }
    return result.toArray(new String[result.size()]);
  }

  private ArrayList<PaletteGroup> collectCurrentGroups(@Nullable VirtualFile selectedFile) {
    ArrayList<PaletteGroup> result = new ArrayList<PaletteGroup>();
    if (selectedFile == null) {
      VirtualFile[] editedFiles = FileEditorManager.getInstance(myProject).getSelectedFiles();
      if (editedFiles.length > 0) {
        selectedFile = editedFiles [0];
      }
    }
    if (selectedFile != null) {
      for(PaletteItemProvider provider: myProviders) {
        PaletteGroup[] groups = provider.getActiveGroups(selectedFile);
        Collections.addAll(result, groups);
      }
    }
    return result;
  }

  public void refreshPaletteIfChanged(VirtualFile selectedFile) {
    Set<PaletteGroup> currentGroups = new HashSet<PaletteGroup>(collectCurrentGroups(selectedFile));
    if (!currentGroups.equals(myGroups)) {
      refreshPalette(selectedFile);
    }
  }

  public int getActiveGroupCount() {
    return myGroups.size();
  }

  public void clearActiveItem() {
    if (getActiveItem() == null) return;
    for(PaletteGroupHeader group: myGroupHeaders) {
      group.getComponentList().clearSelection();
    }
    ListSelectionEvent event = new ListSelectionEvent(this, -1, -1, false);
    PaletteManager.getInstance(myProject).notifySelectionChanged(event);
  }

  @Nullable public PaletteItem getActiveItem() {
    for(PaletteGroupHeader groupHeader: myGroupHeaders) {
      if (groupHeader.isSelected() && groupHeader.getComponentList().getSelectedValue() != null) {
        return (PaletteItem) groupHeader.getComponentList().getSelectedValue();
      }
    }
    return null;
  }

  @Nullable public Object getData(String dataId) {
    if (dataId.equals(DataConstants.PROJECT)) {
      return myProject;
    }
    PaletteItem item = getActiveItem();
    if (item != null) {
      Object data = item.getData(myProject, dataId);
      if (data != null) return data;
    }
    for(PaletteGroupHeader groupHeader: myGroupHeaders) {
      if ((groupHeader.isSelected() && groupHeader.getComponentList().getSelectedValue() != null) || groupHeader == myLastFocusedGroup) {
        return groupHeader.getGroup().getData(myProject, dataId);
      }
    }
    final int tabCount = collectTabNames(myGroups).length;
    if (tabCount > 0) {
      JScrollPane activeScrollPane;
      if (tabCount == 1) {
        activeScrollPane = myScrollPane;
      }
      else {
        activeScrollPane = (JScrollPane) myTabbedPane.getSelectedComponent();
      }
      PaletteContentWindow activeContentWindow = (PaletteContentWindow) activeScrollPane.getViewport().getView();
      PaletteGroupHeader groupHeader = activeContentWindow.getLastGroupHeader();
      if (groupHeader != null) {
        return groupHeader.getGroup().getData(myProject, dataId);
      }
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  void setLastFocusedGroup(final PaletteGroupHeader focusedGroup) {
    myLastFocusedGroup = focusedGroup;
  }

  private class MyListSelectionListener implements ListSelectionListener {
    public void valueChanged(ListSelectionEvent e) {
      PaletteComponentList sourceList = (PaletteComponentList) e.getSource();
      for(int i=e.getFirstIndex(); i <= e.getLastIndex(); i++) {
        if (sourceList.isSelectedIndex(i)) {
          // selection is being added
          for(PaletteGroupHeader group: myGroupHeaders) {
            if (group.getComponentList() != sourceList) {
              group.getComponentList().clearSelection();
            }
          }
          break;
        }
      }
      PaletteManager.getInstance(myProject).notifySelectionChanged(e);
    }
  }

  private class MyPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {
      refreshPalette();
    }
  }

  private static class MyScrollPanePopupHandler extends PopupHandler {
    public void invokePopup(Component comp, int x, int y) {
      JScrollPane scrollPane = (JScrollPane) comp;
      PaletteContentWindow contentWindow = (PaletteContentWindow) scrollPane.getViewport().getView();
      if (contentWindow != null) {
        PaletteGroupHeader groupHeader = contentWindow.getLastGroupHeader();
        if (groupHeader != null) {
          groupHeader.showGroupPopupMenu(comp, x, y);
        }
      }
    }
  }

  private class ClearActiveItemAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      clearActiveItem();
    }
  }
}
