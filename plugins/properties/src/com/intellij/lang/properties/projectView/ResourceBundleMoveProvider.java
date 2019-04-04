// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.lang.properties.projectView;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ResourceBundleMoveProvider extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(ResourceBundleMoveProvider.class);

  @Override
  public boolean canMove(DataContext dataContext) {
    return ResourceBundle.ARRAY_DATA_KEY.getData(dataContext) != null;
  }

  @Override
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
      List<PropertiesFile> propertiesFiles = bundle.getPropertiesFiles();
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
