// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ToolboxUpdaterUI extends ConsoleUpdaterUI {
  @Override
  public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException {
    System.out.println("Validation info:");
    for (ValidationResult item : validationResults) {
      System.out.printf("  %s  %s: %s%n", item.kind, item.path, item.message);
    }

    Map<String, ValidationResult.Option> result = new HashMap<>();
    for (ValidationResult item : validationResults) {
      if (item.options.contains(ValidationResult.Option.REPLACE)) {
        result.put(item.path, ValidationResult.Option.REPLACE);
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
    return result;
  }
}
