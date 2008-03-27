package com.intellij.slicer;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(
    name = "SliceToolwindowSettings",
    storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")}
)
public class SliceToolwindowSettings implements PersistentStateComponent<SliceToolwindowSettings> {
  private boolean isPreview;
  private boolean isAutoScroll;

  public static SliceToolwindowSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SliceToolwindowSettings.class);
  }
  public boolean isPreview() {
    return isPreview;
  }

  public void setPreview(boolean preview) {
    isPreview = preview;
  }

  public boolean isAutoScroll() {
    return isAutoScroll;
  }

  public void setAutoScroll(boolean autoScroll) {
    isAutoScroll = autoScroll;
  }

  public SliceToolwindowSettings getState() {
    return this;
  }

  public void loadState(SliceToolwindowSettings state) {
    isAutoScroll = state.isAutoScroll();
    isPreview = state.isPreview();
  }
}
