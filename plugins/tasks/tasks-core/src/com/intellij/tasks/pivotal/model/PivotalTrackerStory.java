// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.pivotal.model;

import com.intellij.openapi.util.NlsSafe;

public class PivotalTrackerStory {
  private String id;
  private String project_id;
  private String name;
  private String description;
  private String story_type;
  private String current_state;
  private String created_at;
  private String updated_at;

  public String getId() {
    return id;
  }

  public @NlsSafe String getName() {
    return name;
  }

  public @NlsSafe String getDescription() {
    return description;
  }

  public String getStoryType() {
    return story_type;
  }

  public String getCurrentState() {
    return current_state;
  }

  public String getCreatedAt() {
    return created_at;
  }

  public String getUpdatedAt() {
    return updated_at;
  }

  public String getProjectId() {
    return project_id;
  }
}
