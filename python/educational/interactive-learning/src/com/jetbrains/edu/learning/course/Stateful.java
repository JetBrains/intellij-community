package com.jetbrains.edu.learning.course;

public interface Stateful {
  StudyStatus getStatus();
  void setStatus(StudyStatus status, StudyStatus oldStatus);
}
