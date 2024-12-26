// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.actions;

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.clipboard.SimpleTransferable;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.designSurface.tools.PasteTool;
import com.intellij.designer.model.IComponentDeletionParticipant;
import com.intellij.designer.model.IGroupDeleteComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.uiDesigner.SerializedComponentData;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class CommonEditActionsProvider implements DeleteProvider, CopyProvider, PasteProvider, CutProvider {
  private static final DataFlavor DATA_FLAVOR = FileCopyPasteUtil.createJvmDataFlavor(SerializedComponentData.class);

  public static boolean isDeleting;

  private final DesignerEditorPanel myDesigner;

  public CommonEditActionsProvider(DesignerEditorPanel designer) {
    myDesigner = designer;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  protected EditableArea getArea(DataContext dataContext) {
    EditableArea area = EditableArea.DATA_KEY.getData(dataContext);
    return area == null ? myDesigner.getSurfaceArea() : area;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Delete
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    if (myDesigner.getInplaceEditingLayer().isEditing()) {
      return false;
    }
    List<RadComponent> selection = getArea(dataContext).getSelection();
    if (selection.isEmpty()) {
      return false;
    }
    for (RadComponent component : selection) {
      if (!component.canDelete()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void deleteElement(final @NotNull DataContext dataContext) {
    myDesigner.getToolProvider().execute(() -> {
      EditableArea area = getArea(dataContext);
      List<RadComponent> selection = area.getSelection();

      if (selection.isEmpty()) {
        return;
      }

      myDesigner.getToolProvider().loadDefaultTool();
      List<RadComponent> components = RadComponent.getPureSelection(selection);
      updateSelectionBeforeDelete(area, components.get(0), selection);
      handleDeletion(components);
    }, DesignerBundle.message("command.delete.selection"), true);
  }

  private static void handleDeletion(@NotNull List<RadComponent> components) throws Exception {
    // Segment the deleted components into lists of siblings
    Map<RadComponent, List<RadComponent>> siblingLists = RadComponent.groupSiblings(components);

    // Notify parent components about children getting deleted
    for (Map.Entry<RadComponent, List<RadComponent>> entry : siblingLists.entrySet()) {
      RadComponent parent = entry.getKey();
      List<RadComponent> children = entry.getValue();
      boolean finished = false;
      if (parent instanceof IComponentDeletionParticipant handler) {
        finished = handler.deleteChildren(parent, children);
      }
      else if (parent != null && /*check root*/
               parent.getLayout() instanceof IComponentDeletionParticipant handler) {
        finished = handler.deleteChildren(parent, children);
      }

      if (!finished) {
        deleteComponents(children);
      }
    }
  }

  private static void deleteComponents(List<RadComponent> components) throws Exception {
    if (components.get(0) instanceof IGroupDeleteComponent component) {
      component.delete(components);
    }
    else {
      for (RadComponent component : components) {
        component.delete();
      }
    }
  }

  public static void updateSelectionBeforeDelete(EditableArea area, RadComponent component, List<RadComponent> excludes) {
    try {
      isDeleting = true;

      RadComponent newSelection = getNewSelection(component, excludes);
      if (newSelection == null) {
        area.deselectAll();
      }
      else {
        area.select(newSelection);
      }
    }
    finally {
      isDeleting = false;
    }
  }

  private static @Nullable RadComponent getNewSelection(RadComponent component, List<RadComponent> excludes) {
    RadComponent parent = component.getParent();
    if (parent == null) {
      return null;
    }

    List<RadComponent> children = parent.getChildren();
    int size = children.size();
    for (int i = children.indexOf(component) + 1; i < size; i++) {
      RadComponent next = children.get(i);
      if (!excludes.contains(next)) {
        return next;
      }
    }

    return parent;
  }
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Copy
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    if (myDesigner.getInplaceEditingLayer().isEditing()) {
      return false;
    }

    List<RadComponent> selection = getArea(dataContext).getSelection();
    if (selection.isEmpty()) {
      return false;
    }
    return true;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    doCopy(dataContext);
  }

  private boolean doCopy(DataContext dataContext) {
    try {
      Element root = new Element("designer");
      root.setAttribute("target", myDesigner.getPlatformTarget());

      List<RadComponent> components = RadComponent.getPureSelection(getArea(dataContext).getSelection());
      for (RadComponent component : components) {
        component.copyTo(root);
      }

      SerializedComponentData data = new SerializedComponentData(new XMLOutputter().outputString(root));
      CopyPasteManager.getInstance().setContents(new SimpleTransferable(data, DATA_FLAVOR));

      return true;
    }
    catch (Throwable e) {
      myDesigner.showError(DesignerBundle.message("designer.copy.error"), e);
      return false;
    }
  }
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Paste
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return isPasteEnabled(dataContext);
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return !myDesigner.getInplaceEditingLayer().isEditing() && getSerializedComponentData() != null;
  }

  private @Nullable String getSerializedComponentData() {
    try {
      Object transferData = CopyPasteManager.getInstance().getContents(DATA_FLAVOR);
      if (transferData instanceof SerializedComponentData data) {
        String xmlComponents = data.getSerializedComponents();
        if (xmlComponents.startsWith("<designer target=\"" + myDesigner.getPlatformTarget() + "\">")) {
          return xmlComponents;
        }
      }
    }
    catch (Throwable ignored) {
      // ignored
    }

    return null;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    ComponentPasteFactory factory = myDesigner.createPasteFactory(getSerializedComponentData());
    if (factory != null) {
      myDesigner.getToolProvider().setActiveTool(new PasteTool(true, factory));
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Cut
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    return isCopyEnabled(dataContext) && canDeleteElement(dataContext);
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {
    if (doCopy(dataContext)) {
      deleteElement(dataContext);
    }
  }
}