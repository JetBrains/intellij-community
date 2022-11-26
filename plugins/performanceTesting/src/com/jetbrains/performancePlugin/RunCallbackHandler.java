package com.jetbrains.performancePlugin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;

public interface RunCallbackHandler {
  ExtensionPointName<RunCallbackHandler> EP_NAME = ExtensionPointName.create("com.jetbrains.performancePlugin.runCallbackHandler");

  void patchCommandCallback(Project project, ActionCallback callback);

  static void applyPatchesToCommandCallback(Project project, ActionCallback callback) {
    EP_NAME.getExtensionList().forEach(extension ->
      extension.patchCommandCallback(project, callback)
    );
  }
}
