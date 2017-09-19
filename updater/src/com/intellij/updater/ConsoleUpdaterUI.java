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

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class ConsoleUpdaterUI implements UpdaterUI {
  private String myStatus;

  @Override
  public void startProcess(String title) {
    System.out.println(title);
    Runner.logger().info("title: " + title);
  }

  @Override
  public void setProgress(int percentage) { }

  @Override
  public void setProgressIndeterminate() { }

  @Override
  public void setStatus(String status) {
    System.out.println(myStatus = status);
    Runner.logger().info("status: " + status);
  }

  @Override
  public void showError(Throwable e) {
    e.printStackTrace();
  }

  @Override
  public void checkCancelled() { }

  @Override
  public void setDescription(String oldBuildDesc, String newBuildDesc) {
    System.out.println("From " + oldBuildDesc + " to " + newBuildDesc);
  }

  @Override
  public boolean showWarning(String message) {
    System.out.println("Warning: " + message);
    return false;
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

  @Override
  public String toString() {
    return String.format("Status: '%s'", myStatus);
  }
}