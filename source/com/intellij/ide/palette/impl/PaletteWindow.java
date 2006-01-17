package com.intellij.ide.palette.impl;

import com.intellij.ExtensionPoints;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

/**
 * @author yole
 */
public class PaletteWindow extends JPanel implements Scrollable {
  private Project myProject;
  private ArrayList<PaletteGroupHeader> myGroups = new ArrayList<PaletteGroupHeader>();
  private PaletteItemProvider[] myProviders;
  private PaletteWindow.MyPropertyChangeListener myPropertyChangeListener = new MyPropertyChangeListener();

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
    final VirtualFile[] editedFiles = FileEditorManager.getInstance(myProject).getSelectedFiles();

    for(PaletteGroupHeader group: myGroups) {
      remove(group);
      remove(group.getComponentList());
    }
    myGroups.clear();

    if (editedFiles.length > 0) {
      VirtualFile selectedFile = editedFiles [0];
      for(PaletteItemProvider provider: myProviders) {
        PaletteGroup[] groups = provider.getActiveGroups(selectedFile);
        for(PaletteGroup group: groups) {
          PaletteGroupHeader groupHeader = new PaletteGroupHeader(group);
          myGroups.add(groupHeader);
          add(groupHeader);
          PaletteComponentList componentList = new PaletteComponentList(group);
          add(componentList);
          groupHeader.setComponentList(componentList);
          componentList.addListSelectionListener(new MyListSelectionListener());
        }
      }
    }

    revalidate();
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

  public void clearActiveItem() {
    for(PaletteGroupHeader group: myGroups) {
      group.getComponentList().clearSelection();
    }
  }

  @Nullable public PaletteItem getActiveItem() {
    for(PaletteGroupHeader group: myGroups) {
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
      for(PaletteGroupHeader group: myGroups) {
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
      for(PaletteGroupHeader group: myGroups) {
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
          for(PaletteGroupHeader group: myGroups) {
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
