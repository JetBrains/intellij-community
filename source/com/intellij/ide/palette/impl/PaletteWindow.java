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
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;

/**
 * @author yole
 */
public class PaletteWindow extends JPanel implements Scrollable {
  private Project myProject;
  private ArrayList<PaletteGroupHeader> myGroupHeaders = new ArrayList<PaletteGroupHeader>();
  private PaletteItemProvider[] myProviders;
  private PaletteWindow.MyPropertyChangeListener myPropertyChangeListener = new MyPropertyChangeListener();
  private Set<PaletteGroup> myGroups = new HashSet<PaletteGroup>();

  public PaletteWindow(Project project) {
    myProject = project;
    setLayout(new PaletteLayoutManager());
    myProviders = (PaletteItemProvider[]) Extensions.getExtensions(ExtensionPoints.PALETTE_ITEM_PROVIDER, project);
    for(PaletteItemProvider provider: myProviders) {
      provider.addListener(myPropertyChangeListener);
    }

    refreshPalette();
  }

  public void refreshPalette() {
    for(PaletteGroupHeader groupHeader: myGroupHeaders) {
      remove(groupHeader);
      remove(groupHeader.getComponentList());
    }
    myGroupHeaders.clear();
    myGroups.clear();

    for(PaletteGroup group: collectCurrentGroups()) {
      PaletteGroupHeader groupHeader = new PaletteGroupHeader(group);
      myGroupHeaders.add(groupHeader);
      myGroups.add(group);
      add(groupHeader);
      PaletteComponentList componentList = new PaletteComponentList(group);
      add(componentList);
      groupHeader.setComponentList(componentList);
      componentList.addListSelectionListener(new MyListSelectionListener());
    }

    revalidate();
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

  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 20;
  }

  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 100;
  }

  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  public boolean getScrollableTracksViewportHeight() {
    return false;
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

  private class PaletteLayoutManager implements LayoutManager {

    public void addLayoutComponent(String name, Component comp) {
    }

    public void layoutContainer(Container parent) {
      int width = getWidth();

      int height = 0;
      for(PaletteGroupHeader group: myGroupHeaders) {
        group.setLocation(0, height);
        group.setSize(width, group.getPreferredSize().height);
        height += group.getPreferredSize().height;
        if (group.isSelected()) {
          PaletteComponentList componentList = group.getComponentList();
          componentList.setSize(width, componentList.getPreferredSize().height);
          componentList.setLocation(0, height);
          height += componentList.getHeight();
        }
      }
    }

    public Dimension minimumLayoutSize(Container parent) {
      return new Dimension(0, 0);
    }

    public Dimension preferredLayoutSize(Container parent) {
      int height = 0;
      int width = getWidth();
      for(PaletteGroupHeader group: myGroupHeaders) {
        height += group.getHeight();
        if (group.isSelected()) {
          height += group.getComponentList().getPreferredHeight(width);
        }
      }
      return new Dimension(10 /* not used - tracks viewports width*/, height);
    }

    public void removeLayoutComponent(Component comp) {
    }
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
