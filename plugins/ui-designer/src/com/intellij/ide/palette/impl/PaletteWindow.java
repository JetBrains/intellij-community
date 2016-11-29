/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.palette.impl;

import com.intellij.designer.LightToolWindowContent;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
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

/**
 * @author yole
 */
public class PaletteWindow extends JPanel implements LightToolWindowContent, DataProvider {
  private final Project myProject;
  private final ArrayList<PaletteGroupHeader> myGroupHeaders = new ArrayList<>();
  private final PaletteItemProvider[] myProviders;
  private final MyPropertyChangeListener myPropertyChangeListener = new MyPropertyChangeListener();
  private final Set<PaletteGroup> myGroups = new HashSet<>();
  private final JTabbedPane myTabbedPane = new JBTabbedPane();
  private final JScrollPane myScrollPane = ScrollPaneFactory.createScrollPane();
  private final MyListSelectionListener myListSelectionListener = new MyListSelectionListener();
  private PaletteGroupHeader myLastFocusedGroup;

  @NonNls private static final String ourHelpID = "guiDesigner.uiTour.palette";

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
    myProviders = Extensions.getExtensions(PaletteItemProvider.EP_NAME, project);

    setLayout(new GridLayout(1, 1));
    myScrollPane.addMouseListener(new MyScrollPanePopupHandler());
    myScrollPane.setBorder(null);
    KeyStroke escStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    new ClearActiveItemAction().registerCustomShortcutSet(new CustomShortcutSet(escStroke), myScrollPane);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      DragSource.getDefaultDragSource().addDragSourceListener(myDragSourceListener);
    }
  }

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
          ((JComponent)component).revalidate();
        }
      }
    }
    else {
      if (oldTabNames.length <= 1) {
        remove(myScrollPane);
        add(myTabbedPane);
      }
      for (String tabName : tabNames) {
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
    return ArrayUtil.toStringArray(result);
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

  @Nullable
  public PaletteItem getActiveItem() {
    for (PaletteGroupHeader groupHeader : myGroupHeaders) {
      if (groupHeader.isSelected() && groupHeader.getComponentList().getSelectedValue() != null) {
        return (PaletteItem)groupHeader.getComponentList().getSelectedValue();
      }
    }
    return null;
  }

  @Nullable
  public <T extends PaletteItem> T getActiveItem(Class<T> cls) {
    PaletteItem item = getActiveItem();
    if (item != null && item.getClass().isInstance(item)) {
      //noinspection unchecked
      return (T)item;
    }
    return null;
  }

  @Nullable
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return ourHelpID;
    }
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    PaletteItem item = getActiveItem();
    if (item != null) {
      Object data = item.getData(myProject, dataId);
      if (data != null) return data;
    }
    for (PaletteGroupHeader groupHeader : myGroupHeaders) {
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
        activeScrollPane = (JScrollPane)myTabbedPane.getSelectedComponent();
      }
      PaletteContentWindow activeContentWindow = (PaletteContentWindow)activeScrollPane.getViewport().getView();
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
    public void propertyChange(PropertyChangeEvent evt) {
      refreshPalette(myDesigner.getFile());
    }
  }

  private static class MyScrollPanePopupHandler extends PopupHandler {
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
    public void actionPerformed(AnActionEvent e) {
      clearActiveItem();
    }
  }
}
