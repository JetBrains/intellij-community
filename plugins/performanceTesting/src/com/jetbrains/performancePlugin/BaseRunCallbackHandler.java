package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;

public class BaseRunCallbackHandler implements RunCallbackHandler {

  @Override
  public void patchCommandCallback(Project project, ActionCallback callback) { }

}
