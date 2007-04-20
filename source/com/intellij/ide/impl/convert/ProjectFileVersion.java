/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
@State(
  name = ProjectFileVersion.COMPONENT_NAME,
  storages = {
    @Storage(
      id="other",
      file = "$PROJECT_FILE$"
    )
  }
)
public class ProjectFileVersion implements ProjectComponent, PersistentStateComponent<ProjectFileVersion.ProjectFileVersionState> {
  @NonNls public static final String COMPONENT_NAME = "ProjectFileVersion";
  private ProjectFileVersionState myState = new ProjectFileVersionState();

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public ProjectFileVersionState getState() {
    return myState;
  }

  public void loadState(final ProjectFileVersionState object) {
    myState = object;
  }

  public static class ProjectFileVersionState {
    private boolean mySaveInOldFormat;

    public boolean isSaveInOldFormat() {
      return mySaveInOldFormat;
    }

    public void setSaveInOldFormat(final boolean saveInOldFormat) {
      mySaveInOldFormat = saveInOldFormat;
    }
  }
}
