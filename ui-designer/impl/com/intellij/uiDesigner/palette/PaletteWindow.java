package com.intellij.uiDesigner.palette;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class PaletteWindow extends JPanel implements Scrollable {
  private Project myProject;
  private Palette myPalette;
  private ArrayList<PaletteGroup> myGroups = new ArrayList<PaletteGroup>();

  public PaletteWindow(Project project) {
    myProject = project;
    setLayout(new PaletteLayoutManager());
    myPalette = Palette.getInstance(project);
    myPalette.addListener(new Palette.Listener() {
      public void groupsChanged(Palette palette) {
        refreshPalette();
      }
    });

    refreshPalette();
  }

  private void refreshPalette() {
    for(PaletteGroup group: myGroups) {
      remove(group);
      remove(group.getComponentList());
    }
    myGroups.clear();
    for (GroupItem groupItem : myPalette.getGroups()) {
      PaletteGroup group = new PaletteGroup(groupItem);
      myGroups.add(group);
      add(group);
      PaletteComponentList componentList = new PaletteComponentList(groupItem);
      add(componentList);
      group.setComponentList(componentList);
      componentList.addListSelectionListener(new MyListSelectionListener());
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
    for(PaletteGroup group: myGroups) {
      group.getComponentList().clearSelection();
    }
  }

  @Nullable public ComponentItem getActiveItem() {
    for(PaletteGroup group: myGroups) {
      if (group.isSelected() && group.getComponentList().getSelectedValue() != null) {
        return (ComponentItem) group.getComponentList().getSelectedValue();
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
      for(PaletteGroup group: myGroups) {
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
      for(PaletteGroup group: myGroups) {
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
          for(PaletteGroup group: myGroups) {
            if (group.getComponentList() != sourceList) {
              group.getComponentList().clearSelection();
            }
          }
          break;
        }
      }
    }
  }
}
