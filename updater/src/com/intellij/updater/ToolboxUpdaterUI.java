/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
      System.out.println(String.format("  %s  %s: %s", item.kind, item.path, item.message));
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