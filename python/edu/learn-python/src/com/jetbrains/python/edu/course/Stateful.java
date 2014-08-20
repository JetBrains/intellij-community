package com.jetbrains.python.edu.course;

public interface Stateful {
  StudyStatus getStatus();
  void setStatus(StudyStatus status, StudyStatus oldStatus);
}
