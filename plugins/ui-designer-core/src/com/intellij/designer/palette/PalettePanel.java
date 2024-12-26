// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.palette;

import com.intellij.designer.PaletteToolWindowContent;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ScrollPaneFactory;
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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PalettePanel extends JPanel implements UiDataProvider, PaletteToolWindowContent {
  private final JPanel myPaletteContainer = new PaletteContainer();
  private List<PaletteGroupComponent> myGroupComponents = Collections.emptyList();
  private List<PaletteItemsComponent> myItemsComponents = Collections.emptyList();
  private List<PaletteGroup> myGroups = Collections.emptyList();
  private DesignerEditorPanel myDesigner;

  private final FocusListener myFocusListener = new FocusAdapter() {
    @Override
    public void focusGained(FocusEvent e) {
      for (PaletteItemsComponent itemsComponent : myItemsComponents) {
        itemsComponent.clearSelection();
      }
    }
  };
  private final ListSelectionListener mySelectionListener = new ListSelectionListener() {
    @Override
    public void valueChanged(ListSelectionEvent event) {
      notifySelection(event);
    }
  };

  private final DragSourceListener myDragSourceListener = new DragSourceAdapter() {
    @Override
    public void dragDropEnd(DragSourceDropEvent event) {
      Component component = event.getDragSourceContext().getComponent();
      if (!event.getDropSuccess() &&
          component instanceof PaletteItemsComponent &&
          myDesigner != null &&
          myDesigner.getRootPane() == ((JComponent)component).getRootPane()) {
        myDesigner.getToolProvider().loadDefaultTool();
      }
    }
  };

  public PalettePanel() {
    super(new GridLayout(1, 1));

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPaletteContainer);
    scrollPane.setBorder(null);
    add(scrollPane);

    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        clearActiveItem();
      }
    }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, scrollPane);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      DragSource.getDefaultDragSource().addDragSourceListener(myDragSourceListener);
    }
  }

  @Override
  public void dispose() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      DragSource.getDefaultDragSource().removeDragSourceListener(myDragSourceListener);
    }
  }

  public @Nullable PaletteItem getActiveItem() {
    for (PaletteGroupComponent groupComponent : myGroupComponents) {
      if (groupComponent.isSelected()) {
        PaletteItem paletteItem = (PaletteItem)groupComponent.getItemsComponent().getSelectedValue();
        if (paletteItem != null) {
          return paletteItem;
        }
      }
    }
    return null;
  }

  @Override
  public void clearActiveItem() {
    if (getActiveItem() != null) {
      for (PaletteItemsComponent itemsComponent : myItemsComponents) {
        itemsComponent.clearSelection();
      }
      notifySelection(null);
    }
  }

  @Override
  public void refresh() {
    repaint();
  }

  public void loadPalette(@Nullable DesignerEditorPanel designer) {
    if (myDesigner == null && designer == null) {
      return;
    }
    if (myDesigner != null && designer != null && myGroups.equals(designer.getPaletteGroups())) {
      myDesigner = designer;
      restoreSelection();
      return;
    }

    for (PaletteItemsComponent itemsComponent : myItemsComponents) {
      itemsComponent.removeListSelectionListener(mySelectionListener);
    }

    myDesigner = designer;
    myPaletteContainer.removeAll();

    if (designer == null) {
      myGroups = Collections.emptyList();
      myGroupComponents = Collections.emptyList();
      myItemsComponents = Collections.emptyList();
    }
    else {
      myGroups = designer.getPaletteGroups();
      myGroupComponents = new ArrayList<>();
      myItemsComponents = new ArrayList<>();
    }

    for (PaletteGroup group : myGroups) {
      PaletteGroupComponent groupComponent = new PaletteGroupComponent(group);
      PaletteItemsComponent itemsComponent = new PaletteItemsComponent(group, designer);

      groupComponent.setItemsComponent(itemsComponent);
      groupComponent.addFocusListener(myFocusListener);
      myGroupComponents.add(groupComponent);

      itemsComponent.addListSelectionListener(mySelectionListener);
      myItemsComponents.add(itemsComponent);

      myPaletteContainer.add(groupComponent);
      myPaletteContainer.add(itemsComponent);
    }

    myPaletteContainer.revalidate();

    if (myDesigner != null) {
      restoreSelection();
    }
  }

  private void restoreSelection() {
    PaletteItem paletteItem = myDesigner.getActivePaletteItem();
    for (PaletteItemsComponent itemsComponent : myItemsComponents) {
      itemsComponent.restoreSelection(paletteItem);
    }
  }

  private void notifySelection(@Nullable ListSelectionEvent event) {
    if (event != null) {
      PaletteItemsComponent sourceItemsComponent = (PaletteItemsComponent)event.getSource();
      for (int i = event.getFirstIndex(); i <= event.getLastIndex(); i++) {
        if (sourceItemsComponent.isSelectedIndex(i)) {
          for (PaletteItemsComponent itemsComponent : myItemsComponents) {
            if (itemsComponent != sourceItemsComponent) {
              itemsComponent.clearSelection();
            }
          }
          PaletteItem paletteItem = (PaletteItem)sourceItemsComponent.getSelectedValue();
          if (paletteItem != null && !paletteItem.isEnabled()) {
            sourceItemsComponent.clearSelection();
          }
          break;
        }
      }
    }
    if (myDesigner != null) {
      myDesigner.activatePaletteItem(getActiveItem());
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.FILE_EDITOR, myDesigner == null ? null : myDesigner.getEditor());
  }
}