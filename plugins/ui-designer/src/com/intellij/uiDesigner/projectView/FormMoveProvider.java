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

package com.intellij.uiDesigner.projectView;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesHandlerBase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class FormMoveProvider extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.projectView.FormMoveProvider");

  @Override
  public boolean canMove(DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    return forms != null && forms.length > 0;
  }

  @Override
  public void doMove(Project project, PsiElement[] elements, DataContext dataContext) {
    final Set<PsiElement> filesOrDirs = new HashSet<PsiElement>();
    for (PsiElement element : elements) {
      if (element instanceof PsiPackage) continue;
      if (MoveClassesOrPackagesHandlerBase.isPackageOrDirectory(element)) {
        filesOrDirs.add(element);
      } else {
        final PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
          filesOrDirs.add(containingFile);
        }
      }
    }
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    LOG.assertTrue(forms != null);
    PsiClass[] classesToMove = new PsiClass[forms.length];
    PsiFile[] filesToMove = new PsiFile[forms.length];
    for(int i=0; i<forms.length; i++) {
      classesToMove [i] = forms [i].getClassToBind();
      if (classesToMove[i] != null) {
        filesOrDirs.add(classesToMove[i].getContainingFile());
      }
      filesToMove [i] = forms [i].getFormFiles() [0];
      if (filesToMove[i] != null) {
        filesOrDirs.add(filesToMove[i]);
      }
    }

    final PsiElement initialTargetElement = LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext);
    MoveFilesOrDirectoriesUtil
      .doMove(project, filesOrDirs.toArray(new PsiElement[filesOrDirs.size()]), new PsiElement[]{initialTargetElement}, null);
  }

}
