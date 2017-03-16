package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StudyPluginConfigurator {
  ExtensionPointName<StudyPluginConfigurator> EP_NAME = ExtensionPointName.create("Edu.studyPluginConfigurator");

  /**
   * Provide action group that should be placed on the tool window toolbar.
   */
  @NotNull
  DefaultActionGroup getActionGroup(Project project);

  /**
   * @return parameter for CodeMirror script. Available languages: @see <@linktourl http://codemirror.net/mode/>
   */
  @NotNull
  default String getDefaultHighlightingMode(){return "";}

  @Nullable
  StudyAfterCheckAction[] getAfterCheckActions();
  
  @NotNull
  default String getLanguageScriptUrl(){return "";}

  boolean accept(@NotNull final Project project);
}
