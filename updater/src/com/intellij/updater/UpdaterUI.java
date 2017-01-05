/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.List;
import java.util.Map;

public interface UpdaterUI {
  void startProcess(String title);

  void setProgress(int percentage);

  void setProgressIndeterminate();

  void setStatus(String status);

  void showError(Throwable e);

  void checkCancelled() throws OperationCancelledException;

  void setDescription(String oldBuildDesc, String newBuildDesc);

  /**
   * Shows a warning associated with the pretense of a file and asks the user if the validation needs be retried.
   * This function will return true iff the user wants to retry.
   * @param message The warning message to display.
   * @return true if the validation needs to be retried or false if te updater should quit.
   */
  boolean showWarning(String message);

  Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException;
}