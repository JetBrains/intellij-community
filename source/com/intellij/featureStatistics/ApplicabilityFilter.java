package com.intellij.featureStatistics;

import com.intellij.openapi.project.Project;

public interface ApplicabilityFilter {
  boolean isApplicable(String featureId, Project project);
}