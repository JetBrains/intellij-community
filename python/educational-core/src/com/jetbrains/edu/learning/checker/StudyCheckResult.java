package com.jetbrains.edu.learning.checker;

import com.jetbrains.edu.learning.courseFormat.StudyStatus;

public class StudyCheckResult {
  private StudyStatus myStatus;
  private String myMessage;

  public StudyCheckResult(StudyStatus status, String message) {
    myStatus = status;
    myMessage = message;
  }

  public StudyStatus getStatus() {
    return myStatus;
  }

  public String getMessage() {
    return myMessage;
  }
}
