// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class ConsoleUpdaterUI implements UpdaterUI {
  @Override
  public void setDescription(String oldBuildDesc, String newBuildDesc) {
    setDescription("From " + oldBuildDesc + " to " + newBuildDesc);
  }

  @Override
  public void setDescription(String text) {
    if (!text.isEmpty()) {
      System.out.println(text);
    }
  }

  @Override
  public void startProcess(String title) {
    System.out.println(title);
  }

  @Override
  public void setProgress(int percentage) { }

  @Override
  public void setProgressIndeterminate() { }

  @Override
  public void checkCancelled() throws OperationCancelledException { }

  @Override
  public void showError(String message) {
    System.err.println("Error: " + message);
  }

  @Override
  public void askUser(String message) throws OperationCancelledException {
    System.out.println("Warning: " + message);
    throw new OperationCancelledException();
  }

  @Override
  public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException {
    boolean hasErrors = false, hasConflicts = false;

    System.out.println("Validation info:");
    for (ValidationResult item : validationResults) {
      System.out.println(String.format("  %s  %s: %s", item.kind, item.path, item.message));
      if (item.kind == ValidationResult.Kind.ERROR) hasErrors = true;
      if (item.kind == ValidationResult.Kind.CONFLICT) hasConflicts = true;
    }

    if (hasErrors) {
      System.out.println("Invalid files were detected. Failing.");
      throw new OperationCancelledException();
    }

    if (hasConflicts) {
      System.out.println("Conflicting files were detected. Failing.");
      throw new OperationCancelledException();
    }

    return Collections.emptyMap();
  }
}