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
import com.intellij.designer.clipboard.SerializedComponentData;
import com.intellij.designer.clipboard.SimpleTransferable;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.designSurface.tools.PasteTool;
import com.intellij.designer.model.RadComponent;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.ThrowableRunnable;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
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
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Delete
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean canDeleteElement(DataContext dataContext) {
    // TODO: InplaceEditing
    List<RadComponent> selection = myDesigner.getActionsArea().getSelection();
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
  public void deleteElement(DataContext dataContext) {
    CommandProcessor.getInstance().executeCommand(myDesigner.getProject(), new Runnable() {
      public void run() {
        myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
          @Override
          public void run() throws Exception {
            EditableArea area = myDesigner.getActionsArea();
            List<RadComponent> selection = area.getSelection();
            List<RadComponent> components = RadComponent.getPureSelection(selection);
            RadComponent newSelection = getNewSelection(components.get(0), selection);

            for (RadComponent component : components) {
              component.delete();
            }

            if (newSelection == null) {
              area.deselectAll();
            }
            else {
              area.select(newSelection);
            }
          }
        });
      }
    }, DesignerBundle.message("command.delete.selection"), null);
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
  public boolean isCopyVisible(DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isCopyEnabled(DataContext dataContext) {
    // TODO: InplaceEditing
    return !myDesigner.getActionsArea().getSelection().isEmpty();
  }

  @Override
  public void performCopy(DataContext dataContext) {
    doCopy();
  }

  private boolean doCopy() {
    return myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        Element root = new Element("designer");
        root.setAttribute("target", myDesigner.getPlatformTarget());

        List<RadComponent> components = RadComponent.getPureSelection(myDesigner.getActionsArea().getSelection());
        for (RadComponent component : components) {
          component.copyTo(root);
        }

        SerializedComponentData data = new SerializedComponentData(new XMLOutputter().outputString(root));
        CopyPasteManager.getInstance().setContents(new SimpleTransferable(data, DATA_FLAVOR));
      }
    });
  }
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Paste
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean isPastePossible(DataContext dataContext) {
    return isPasteEnabled(dataContext);
  }

  @Override
  public boolean isPasteEnabled(DataContext dataContext) {
    // TODO: InplaceEditing
    return getSerializedComponentData() != null;
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
  public void performPaste(DataContext dataContext) {
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
  public boolean isCutVisible(DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isCutEnabled(DataContext dataContext) {
    return isCopyEnabled(dataContext) && canDeleteElement(dataContext);
  }

  @Override
  public void performCut(DataContext dataContext) {
    if (doCopy()) {
      deleteElement(dataContext);
    }
  }
}