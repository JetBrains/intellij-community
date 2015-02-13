package com.jetbrains.edu.courseFormat;

public interface StudyStateful {
  StudyStatus getStatus();
  void setStatus(StudyStatus status, StudyStatus oldStatus);
}
