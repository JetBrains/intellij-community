package com.intellij.updater;

import java.util.Map;
import java.util.List;
import java.util.Collections;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class ConsoleUpdaterUI implements UpdaterUI {
  private String myStatus;

  public void startProcess(String title) {
    System.out.println(title);
    Runner.logger.trace("title: " + title);
  }

  public void setProgress(int percentage) {
  }

  public void setProgressIndeterminate() {
  }

  public void setStatus(String status) {
    System.out.println(myStatus = status);
    Runner.logger.trace("status: " + status);
  }

  public void showError(Throwable e) {
    Runner.logger.error(e.fillInStackTrace());
    e.printStackTrace();
  }

  public void checkCancelled() throws OperationCancelledException {
  }

  public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) {
    return Collections.emptyMap();
  }

  @Override
  public String toString() {
    Runner.logger.trace("Status: '" + myStatus + '\'');
    return "Status: '" + myStatus + '\'';
  }
}
