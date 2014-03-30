/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.properties.projectView;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: Aug 26, 2010
 */
public class ResourceBundleMoveProvider extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#" + ResourceBundleMoveProvider.class.getName());

  @Override
  public boolean canMove(DataContext dataContext) {
    return ResourceBundle.ARRAY_DATA_KEY.getData(dataContext) != null;
  }

  public boolean canMove(PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    return false;
  }

  @Override
  public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources) {
    return MoveFilesOrDirectoriesHandler.isValidTarget(psiElement);
  }

  @Override
  public void collectFilesOrDirsFromContext(DataContext dataContext, Set<PsiElement> filesOrDirs) {

    final ResourceBundle[] bundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    LOG.assertTrue(bundles != null);
    for (ResourceBundle bundle : bundles) {
      List<PropertiesFile> propertiesFiles = bundle.getPropertiesFiles(CommonDataKeys.PROJECT.getData(dataContext));
      for (PropertiesFile propertiesFile : propertiesFiles) {
        filesOrDirs.add(propertiesFile.getContainingFile());
      }
    }
  }

  @Override
  public boolean isMoveRedundant(PsiElement source, PsiElement target) {
    if (source instanceof PropertiesFile && target instanceof PsiDirectory) {
      return source.getParent() == target;
    }
    return super.isMoveRedundant(source, target);
  }
}
