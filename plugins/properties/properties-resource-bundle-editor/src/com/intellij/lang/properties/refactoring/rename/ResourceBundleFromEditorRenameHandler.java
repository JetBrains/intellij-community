// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.*;
import com.intellij.lang.properties.structureView.PropertiesPrefixGroup;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleFromEditorRenameHandler implements RenameHandler {

  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }
    final ResourceBundle bundle = ResourceBundleUtil.getResourceBundleFromDataContext(dataContext);
    if (bundle == null) {
      return false;
    }
    final FileEditor fileEditor = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
    if (!(fileEditor instanceof ResourceBundleEditor)) {
      return false;
    }
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    return virtualFile instanceof ResourceBundleAsVirtualFile;
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, final PsiFile file, DataContext dataContext) {
    final ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
    assert resourceBundleEditor != null;
    final Object selectedElement = resourceBundleEditor.getSelectedElementIfOnlyOne();
    if (selectedElement != null) {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        if (selectedElement instanceof PropertiesPrefixGroup group) {
          ResourceBundleRenameUtil.renameResourceBundleKeySection(getPsiElementsFromGroup(group),
                                                                  group.getPresentableName(),
                                                                  group.getPrefix().length() - group.getPresentableName().length());
        } else if (selectedElement instanceof PropertyStructureViewElement) {
          final PsiElement psiElement = ((PropertyStructureViewElement)selectedElement).getPsiElement();
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
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }

  private static List<PsiElement> getPsiElementsFromGroup(final PropertiesPrefixGroup propertiesPrefixGroup) {
    return ContainerUtil.mapNotNull(propertiesPrefixGroup.getChildren(), treeElement -> {
      if (treeElement instanceof PropertyStructureViewElement) {
        return ((PropertyStructureViewElement)treeElement).getPsiElement();
      }
      return null;
    });
  }
}