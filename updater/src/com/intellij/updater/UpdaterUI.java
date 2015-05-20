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