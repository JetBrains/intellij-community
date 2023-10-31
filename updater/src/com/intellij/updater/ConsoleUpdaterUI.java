// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ConsoleUpdaterUI implements UpdaterUI {
  private final boolean myForceReplace;

  public ConsoleUpdaterUI(boolean forceReplace) {
    myForceReplace = forceReplace;
  }

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
  public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException {
    System.out.println("Validation info:");
    for (var item : validationResults) {
      System.out.printf("  %s  %s: %s%n", item.kind, item.path, item.message);
    }

    var choices = new HashMap<String, ValidationResult.Option>();
    for (var item : validationResults) {
      if (myForceReplace && item.options.contains(ValidationResult.Option.REPLACE)) {
        choices.put(item.path, ValidationResult.Option.REPLACE);
        System.out.println("Selected REPLACE for " + item.path);
        continue;
      }
      if (item.kind == ValidationResult.Kind.ERROR) {
        System.out.println("Invalid files were detected. Failing.");
        throw new OperationCancelledException();
      }
      if (item.kind == ValidationResult.Kind.CONFLICT) {
        System.out.println("Conflicting files were detected. Failing.");
        throw new OperationCancelledException();
      }
    }
    return choices;
  }
}
