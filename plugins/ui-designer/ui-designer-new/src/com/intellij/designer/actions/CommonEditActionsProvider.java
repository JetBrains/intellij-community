/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.actions;

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.clipboard.SimpleTransferable;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.designSurface.tools.PasteTool;
import com.intellij.designer.model.IGroupDeleteComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.uiDesigner.SerializedComponentData;
import com.intellij.util.ThrowableRunnable;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class CommonEditActionsProvider implements DeleteProvider, CopyProvider, PasteProvider, CutProvider {
  private static final DataFlavor DATA_FLAVOR = FileCopyPasteUtil.createJvmDataFlavor(SerializedComponentData.class);

  private final DesignerEditorPanel myDesigner;

  public CommonEditActionsProvider(DesignerEditorPanel designer) {
    myDesigner = designer;
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
    myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        EditableArea area = getArea(dataContext);
        List<RadComponent> selection = area.getSelection();

        if (selection.isEmpty()) {
          return;
        }

        myDesigner.getToolProvider().loadDefaultTool();

        List<RadComponent> components = RadComponent.getPureSelection(selection);

        RadComponent newSelection = getNewSelection(components.get(0), selection);
        if (newSelection == null) {
          area.deselectAll();
        }
        else {
          area.select(newSelection);
        }

        if (components.get(0) instanceof IGroupDeleteComponent) {
          ((IGroupDeleteComponent)components.get(0)).delete(components);
        }
        else {
          for (RadComponent component : components) {
            component.delete();
          }
        }
      }
    }, DesignerBundle.message("command.delete.selection"), true);
  }

  @Nullable
  private static RadComponent getNewSelection(RadComponent component, List<RadComponent> excludes) {
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
    return !myDesigner.getInplaceEditingLayer().isEditing() && !getArea(dataContext).getSelection().isEmpty();
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
      myDesigner.showError("Copy error", e);
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

  @Nullable
  private String getSerializedComponentData() {
    try {
      CopyPasteManager copyPasteManager = CopyPasteManager.getInstance();
      if (!copyPasteManager.isDataFlavorAvailable(DATA_FLAVOR)) {
        return null;
      }

      Transferable content = copyPasteManager.getContents();
      if (content == null) {
        return null;
      }

      Object transferData = content.getTransferData(DATA_FLAVOR);
      if (transferData instanceof SerializedComponentData) {
        SerializedComponentData data = (SerializedComponentData)transferData;
        String xmlComponents = data.getSerializedComponents();
        if (xmlComponents.startsWith("<designer target=\"" + myDesigner.getPlatformTarget() + "\">")) {
          return xmlComponents;
        }
      }
    }
    catch (Throwable e) {
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