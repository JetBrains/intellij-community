package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.concurrency.Promise;

import java.util.List;

public interface ImportChooser {
  static ImportChooser getInstance() {
    return ApplicationManager.getApplication().getService(ImportChooser.class);
  }

  Promise<ImportCandidateHolder> selectImport(List<? extends ImportCandidateHolder> mySources,
                                              String myName,
                                              boolean myUseQualifiedImport,
                                              PsiElement myTarget);
}
