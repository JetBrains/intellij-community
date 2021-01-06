package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.concurrency.Promise;

import java.util.List;

public interface ImportChooser {
  static ImportChooser getInstance() {
    return ApplicationManager.getApplication().getService(ImportChooser.class);
  }

  Promise<ImportCandidateHolder> selectImport(List<? extends ImportCandidateHolder> mySources, boolean myUseQualifiedImport);
}
