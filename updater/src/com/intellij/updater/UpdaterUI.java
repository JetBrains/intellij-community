// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.util.List;
import java.util.Map;

public interface UpdaterUI {
  void setDescription(String oldBuildDesc, String newBuildDesc);

  void startProcess(String title);
  void setProgress(int percentage);
  void setProgressIndeterminate();
  void checkCancelled() throws OperationCancelledException;

  void showError(String message);

  void askUser(String message) throws OperationCancelledException;
  Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException;

  default String bold(String text) { return text; }
}