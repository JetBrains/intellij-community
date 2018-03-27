/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/**
 * @author Alexey
 */
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.*;
import com.intellij.lang.properties.structureView.PropertiesPrefixGroup;
import com.intellij.lang.properties.structureView.PropertiesStructureViewElement;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleFromEditorRenameHandler implements RenameHandler {

  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }
    final ResourceBundle bundle = ResourceBundleUtil.getResourceBundleFromDataContext(dataContext);
    if (bundle == null) {
      return false;
    }
    final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    if (!(fileEditor instanceof ResourceBundleEditor)) {
      return false;
    }
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    return !(!(virtualFile instanceof ResourceBundleAsVirtualFile));
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, final PsiFile file, DataContext dataContext) {
    final ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    assert resourceBundleEditor != null;
    final ResourceBundleEditorViewElement selectedElement = resourceBundleEditor.getSelectedElementIfOnlyOne();
    if (selectedElement != null) {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        if (selectedElement instanceof PropertiesPrefixGroup) {
          final PropertiesPrefixGroup group = (PropertiesPrefixGroup)selectedElement;
          ResourceBundleRenameUtil.renameResourceBundleKeySection(getPsiElementsFromGroup(group),
                                                                  group.getPresentableName(),
                                                                  group.getPrefix().length() - group.getPresentableName().length());
        } else if (selectedElement instanceof ResourceBundlePropertyStructureViewElement) {
          final PsiElement psiElement = ((ResourceBundlePropertyStructureViewElement)selectedElement).getPsiElement();
          ResourceBundleRenameUtil.renameResourceBundleKey(psiElement, project);
        } else if (selectedElement instanceof ResourceBundleFileStructureViewElement) {
          ResourceBundleRenameUtil.renameResourceBundleBaseName(((ResourceBundleFileStructureViewElement)selectedElement).getValue(), project);
        } else {
          throw new IllegalStateException("unsupported type: " + selectedElement.getClass());
        }
      });
    }
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }

  private static List<PsiElement> getPsiElementsFromGroup(final PropertiesPrefixGroup propertiesPrefixGroup) {
    return ContainerUtil.mapNotNull(propertiesPrefixGroup.getChildren(), (NullableFunction<TreeElement, PsiElement>)treeElement -> {
      if (treeElement instanceof PropertiesStructureViewElement) {
        return ((PropertiesStructureViewElement)treeElement).getValue().getPsiElement();
      }
      if (treeElement instanceof ResourceBundlePropertyStructureViewElement) {
        return ((ResourceBundlePropertyStructureViewElement)treeElement).getPsiElement();
      }
      return null;
    });
  }
}
