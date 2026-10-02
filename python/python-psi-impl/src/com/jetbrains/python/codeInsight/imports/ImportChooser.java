// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.concurrency.Promise;

import java.util.List;

@ApiStatus.Internal
public interface ImportChooser {
  static ImportChooser getInstance() {
    return ApplicationManager.getApplication().getService(ImportChooser.class);
  }

  Promise<ImportCandidateHolder> selectImport(List<? extends ImportCandidateHolder> mySources, boolean myUseQualifiedImport);
}
