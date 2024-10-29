// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.palette.impl;

import com.intellij.designer.LightToolWindowContent;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;


public class PaletteWindow extends JPanel implements LightToolWindowContent, UiDataProvider {
  private final Project myProject;
  private final ArrayList<PaletteGroupHeader> myGroupHeaders = new ArrayList<>();
  private final PaletteItemProvider[] myProviders;
  private final MyPropertyChangeListener myPropertyChangeListener = new MyPropertyChangeListener();
  private final Set<PaletteGroup> myGroups = new HashSet<>();
  private final JTabbedPane myTabbedPane = new JBTabbedPane();
  private final JScrollPane myScrollPane = ScrollPaneFactory.createScrollPane();
  private final MyListSelectionListener myListSelectionListener = new MyListSelectionListener();
  private PaletteGroupHeader myLastFocusedGroup;

  private static final @NonNls String ourHelpID = "guiDesigner.uiTour.palette";

  private final DragSourceListener myDragSourceListener = new DragSourceAdapter() {
    @Override
    public void dragDropEnd(DragSourceDropEvent event) {
      Component component = event.getDragSourceContext().getComponent();
      if (!event.getDropSuccess() &&
          component instanceof PaletteComponentList &&
          getRootPane() == ((JComponent)component).getRootPane()) {
        clearActiveItem();
      }
    }
  };

  private GuiEditor myDesigner;

  public PaletteWindow(Project project) {
    myProject = project;
    myProviders = PaletteItemProvider.EP_NAME.getExtensions(project);

    setLayout(new GridLayout(1, 1));
    myScrollPane.addMouseListener(new MyScrollPanePopupHandler());
    myScrollPane.setBorder(null);
    KeyStroke escStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    new ClearActiveItemAction().registerCustomShortcutSet(new CustomShortcutSet(escStroke), myScrollPane);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      DragSource.getDefaultDragSource().addDragSourceListener(myDragSourceListener);
    }
  }

  @Override
  public void dispose() {
    removePaletteProviderListener();

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      DragSource.getDefaultDragSource().removeDragSourceListener(myDragSourceListener);
    }
  }

  private void addPaletteProviderListener() {
    for (PaletteItemProvider provider : myProviders) {
      provider.addListener(myPropertyChangeListener);
    }
  }

  private void removePaletteProviderListener() {
    for (PaletteItemProvider provider : myProviders) {
      provider.removeListener(myPropertyChangeListener);
    }
  }

  public void refreshPaletteIfChanged(@Nullable GuiEditor designer) {
    removePaletteProviderListener();
    myDesigner = designer;
    if (designer != null) {
      addPaletteProviderListener();
    }

    VirtualFile file = designer == null ? null : designer.getFile();
    Set<PaletteGroup> currentGroups = new HashSet<>(collectCurrentGroups(file));
    if (!currentGroups.equals(myGroups)) {
      refreshPalette(file);
    }
  }

  private void refreshPalette(@Nullable VirtualFile selectedFile) {
    for (PaletteGroupHeader groupHeader : myGroupHeaders) {
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

      for (PaletteGroup group : currentGroups) {
        addGroupToControl(group, contentWindow);
      }

      final JComponent view = (JComponent)myScrollPane.getViewport().getView();
      if (view != null) {
        view.revalidate();
        for (Component component : view.getComponents()) {
          component.revalidate();
        }
      }
    }
    else {
      if (oldTabNames.length <= 1) {
        remove(myScrollPane);
        add(myTabbedPane);
      }
      for (@NlsSafe String tabName : tabNames) {
        PaletteContentWindow contentWindow = new PaletteContentWindow();
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(contentWindow);
        scrollPane.addMouseListener(new MyScrollPanePopupHandler());
        myTabbedPane.add(tabName, scrollPane);
        for (PaletteGroup group : currentGroups) {
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
    PaletteComponentList componentList = new PaletteComponentList(myProject, this, group);
    control.add(componentList);
    groupHeader.setComponentList(componentList);
    componentList.addListSelectionListener(myListSelectionListener);
  }

  private static String[] collectTabNames(final Collection<PaletteGroup> groups) {
    Set<String> result = new TreeSet<>();
    for (PaletteGroup group : groups) {
      result.add(group.getTabName());
    }
    return ArrayUtilRt.toStringArray(result);
  }

  private ArrayList<PaletteGroup> collectCurrentGroups(@Nullable VirtualFile selectedFile) {
    ArrayList<PaletteGroup> result = new ArrayList<>();
    if (selectedFile != null) {
      for (PaletteItemProvider provider : myProviders) {
        PaletteGroup[] groups = provider.getActiveGroups(selectedFile);
        Collections.addAll(result, groups);
      }
    }
    return result;
  }

  public void clearActiveItem() {
    if (getActiveItem() == null) return;
    for (PaletteGroupHeader group : myGroupHeaders) {
      group.getComponentList().clearSelection();
    }
    ListSelectionEvent event = new ListSelectionEvent(this, -1, -1, false);
    notifySelectionChanged(event);
  }

  public @Nullable PaletteItem getActiveItem() {
    for (PaletteGroupHeader groupHeader : myGroupHeaders) {
      if (groupHeader.isSelected() && groupHeader.getComponentList().getSelectedValue() != null) {
        return (PaletteItem)groupHeader.getComponentList().getSelectedValue();
      }
    }
    return null;
  }

  public @Nullable <T extends PaletteItem> T getActiveItem(Class<T> cls) {
    PaletteItem item = getActiveItem();
    if (item != null && item.getClass().isInstance(item)) {
      //noinspection unchecked
      return (T)item;
    }
    return null;
  }

  private @Nullable PaletteGroup getActiveGroup() {
    for (PaletteGroupHeader groupHeader : myGroupHeaders) {
      if ((groupHeader.isSelected() && groupHeader.getComponentList().getSelectedValue() != null) || groupHeader == myLastFocusedGroup) {
        return groupHeader.getGroup();
      }
    }
    int tabCount = collectTabNames(myGroups).length;
    if (tabCount > 0) {
      JScrollPane activeScrollPane;
      if (tabCount == 1) {
        activeScrollPane = myScrollPane;
      }
      else {
        activeScrollPane = (JScrollPane)myTabbedPane.getSelectedComponent();
      }
      PaletteContentWindow activeContentWindow = (PaletteContentWindow)activeScrollPane.getViewport().getView();
      PaletteGroupHeader groupHeader = activeContentWindow.getLastGroupHeader();
      if (groupHeader != null) {
        return groupHeader.getGroup();
      }
    }
    return null;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, ourHelpID);
    sink.set(CommonDataKeys.PROJECT, myProject);
    DataSink.uiDataSnapshot(sink, getActiveItem());
    DataSink.uiDataSnapshot(sink, getActiveGroup());
  }

  public Project getProject() {
    return myProject;
  }

  void setLastFocusedGroup(final PaletteGroupHeader focusedGroup) {
    myLastFocusedGroup = focusedGroup;
    for (PaletteGroupHeader group : myGroupHeaders) {
      group.getComponentList().clearSelection();
    }
  }

  void notifyKeyEvent(final KeyEvent e) {
    if (myDesigner != null) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        myDesigner.paletteKeyPressed(e);
      }
      else if (e.getID() == KeyEvent.KEY_RELEASED) {
        myDesigner.paletteKeyReleased(e);
      }
    }
  }

  void notifyDropActionChanged(int gestureModifiers) {
    if (myDesigner != null) {
      myDesigner.paletteDropActionChanged(gestureModifiers);
    }
  }

  void notifySelectionChanged(final ListSelectionEvent event) {
    if (myDesigner != null) {
      myDesigner.paletteValueChanged(event);
    }
  }

  private class MyListSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      PaletteComponentList sourceList = (PaletteComponentList)e.getSource();
      for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
        if (sourceList.isSelectedIndex(i)) {
          // selection is being added
          for (PaletteGroupHeader group : myGroupHeaders) {
            if (group.getComponentList() != sourceList) {
              group.getComponentList().clearSelection();
            }
          }
          break;
        }
      }
      notifySelectionChanged(e);
    }
  }

  private class MyPropertyChangeListener implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      refreshPalette(myDesigner.getFile());
    }
  }

  private static class MyScrollPanePopupHandler extends PopupHandler {
    @Override
    public void invokePopup(Component comp, int x, int y) {
      JScrollPane scrollPane = (JScrollPane)comp;
      PaletteContentWindow contentWindow = (PaletteContentWindow)scrollPane.getViewport().getView();
      if (contentWindow != null) {
        PaletteGroupHeader groupHeader = contentWindow.getLastGroupHeader();
        if (groupHeader != null) {
          groupHeader.showGroupPopupMenu(comp, x, y);
        }
      }
    }
  }

  private class ClearActiveItemAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      clearActiveItem();
    }
  }
}