/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.IProperty;
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
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTarget;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    if (fileEditor == null || !(fileEditor instanceof ResourceBundleEditor)) {
      return false;
    }
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    return !(virtualFile == null || !(virtualFile instanceof ResourceBundleAsVirtualFile));
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
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          if (selectedElement instanceof PropertiesPrefixGroup) {
            final PropertiesPrefixGroup group = (PropertiesPrefixGroup)selectedElement;
            ResourceBundleRenameUtil.renameResourceBundleKeySection(getPsiElementsFromGroup(group),
                                                                    group.getPresentableName(),
                                                                    group.getPrefix().length() - group.getPresentableName().length());
          } else if (selectedElement instanceof ResourceBundlePropertyStructureViewElement) {
            final PsiElement psiElement = ((ResourceBundlePropertyStructureViewElement)selectedElement).getProperty().getPsiElement();
            ResourceBundleRenameUtil.renameResourceBundleKey(psiElement, project);
          } else if (selectedElement instanceof ResourceBundleFileStructureViewElement) {
            ResourceBundleRenameUtil.renameResourceBundleBaseName(((ResourceBundleFileStructureViewElement)selectedElement).getValue(), project);
          } else {
            throw new IllegalStateException("unsupported type: " + selectedElement.getClass());
          }
        }
      });
    }
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }

  private static List<PsiElement> getPsiElementsFromGroup(final PropertiesPrefixGroup propertiesPrefixGroup) {
    return ContainerUtil.mapNotNull(propertiesPrefixGroup.getChildren(), new NullableFunction<TreeElement, PsiElement>() {
      @Nullable
      @Override
      public PsiElement fun(TreeElement treeElement) {
        if (treeElement instanceof PropertiesStructureViewElement) {
          return ((PropertiesStructureViewElement)treeElement).getValue().getPsiElement();
        }
        if (treeElement instanceof ResourceBundlePropertyStructureViewElement) {
          return ((ResourceBundlePropertyStructureViewElement)treeElement).getProperty().getPsiElement();
        }
        return null;
      }
    });
  }
}
