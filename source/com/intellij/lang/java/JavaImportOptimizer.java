/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.java;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class JavaImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.java.JavaImportOptimizer");

  @NotNull
  public Runnable processFile(final PsiFile file) {
    if (file instanceof PsiJavaFile) {
      Project project = file.getProject();
      final PsiImportList newImportList = JavaCodeStyleManager.getInstance(project).prepareOptimizeImportsResult((PsiJavaFile)file);
      return new Runnable() {
        public void run() {
          try {
            if (newImportList != null) {
              final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
              final Document document = manager.getDocument(file);
              if (document != null) {
                manager.commitDocument(document);
              }
              final PsiImportList oldImportList = ((PsiJavaFile)file).getImportList();
              assert oldImportList != null;
              oldImportList.replace(newImportList);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      };
    }
    else {
      return EmptyRunnable.getInstance();
    }
  }
}
