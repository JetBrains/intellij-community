package com.intellij.ide.palette.impl;

import com.intellij.ExtensionPoints;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * @author yole
 */
public class PaletteWindow extends JPanel {
  private Project myProject;
  private ArrayList<PaletteGroupHeader> myGroupHeaders = new ArrayList<PaletteGroupHeader>();
  private PaletteItemProvider[] myProviders;
  private PaletteWindow.MyPropertyChangeListener myPropertyChangeListener = new MyPropertyChangeListener();
  private Set<PaletteGroup> myGroups = new HashSet<PaletteGroup>();
  private JTabbedPane myTabbedPane = new JTabbedPane();
  private JScrollPane myScrollPane = new JScrollPane();
  private MyListSelectionListener myListSelectionListener = new MyListSelectionListener();

  public PaletteWindow(Project project) {
    myProject = project;
    myProviders = (PaletteItemProvider[]) Extensions.getExtensions(ExtensionPoints.PALETTE_ITEM_PROVIDER, project);
    for(PaletteItemProvider provider: myProviders) {
      provider.addListener(myPropertyChangeListener);
    }

    setLayout(new GridLayout(1, 1));

    refreshPalette();
  }

  public void refreshPalette() {
    for(PaletteGroupHeader groupHeader: myGroupHeaders) {
      groupHeader.getComponentList().removeListSelectionListener(myListSelectionListener);
    }
    String[] oldTabNames = collectTabNames(myGroups);
    if (oldTabNames.length == 1) {
      remove(myScrollPane);
    }
    else {
      myTabbedPane.removeAll();
      remove(myTabbedPane);
    }
    myGroupHeaders.clear();
    myGroups.clear();

    final ArrayList<PaletteGroup> currentGroups = collectCurrentGroups();
    String[] tabNames = collectTabNames(currentGroups);
    if (tabNames.length == 1) {
      PaletteContentWindow contentWindow = new PaletteContentWindow();
      myScrollPane.getViewport().setView(contentWindow);
      add(myScrollPane);

      for(PaletteGroup group: currentGroups) {
        addGroupToControl(group, contentWindow);
      }
    }
    else {
      add(myTabbedPane);
      for(String tabName: tabNames) {
        PaletteContentWindow contentWindow = new PaletteContentWindow();
        JScrollPane scrollPane = new JScrollPane(contentWindow);
        myTabbedPane.add(tabName, scrollPane);
        for(PaletteGroup group: currentGroups) {
          if (group.getTabName().equals(tabName)) {
            addGroupToControl(group, contentWindow);
          }
        }
      }
    }

    revalidate();
  }

  private void addGroupToControl(PaletteGroup group, JComponent control) {
    PaletteGroupHeader groupHeader = new PaletteGroupHeader(group);
    myGroupHeaders.add(groupHeader);
    myGroups.add(group);
    control.add(groupHeader);
    PaletteComponentList componentList = new PaletteComponentList(group);
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

  private ArrayList<PaletteGroup> collectCurrentGroups() {
    ArrayList<PaletteGroup> result = new ArrayList<PaletteGroup>();
    VirtualFile[] editedFiles = FileEditorManager.getInstance(myProject).getSelectedFiles();
    if (editedFiles.length > 0) {
      VirtualFile selectedFile = editedFiles [0];
      for(PaletteItemProvider provider: myProviders) {
        PaletteGroup[] groups = provider.getActiveGroups(selectedFile);
        Collections.addAll(result, groups);
      }
    }
    return result;
  }

  public void refreshPaletteIfChanged() {
    Set<PaletteGroup> currentGroups = new HashSet<PaletteGroup>(collectCurrentGroups());
    if (!currentGroups.equals(myGroups)) {
      refreshPalette();
    }
  }

  public int getActiveGroupCount() {
    return myGroups.size();
  }

  public void clearActiveItem() {
    for(PaletteGroupHeader group: myGroupHeaders) {
      group.getComponentList().clearSelection();
    }
  }

  @Nullable public PaletteItem getActiveItem() {
    for(PaletteGroupHeader group: myGroupHeaders) {
      if (group.isSelected() && group.getComponentList().getSelectedValue() != null) {
        return (PaletteItem) group.getComponentList().getSelectedValue();
      }
    }
    return null;
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
    }
  }

  private class MyPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {
      refreshPalette();
    }
  }
}
