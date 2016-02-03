package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface StudyToolWindowConfigurator {
  ExtensionPointName<StudyToolWindowConfigurator> EP_NAME = ExtensionPointName.create("Edu.studyToolWindowConfigurator");
  
  @NotNull
  DefaultActionGroup getActionGroup(Project project);
  
  @NotNull
  HashMap<String, JPanel> getAdditionalPanels(Project project);

  @NotNull
  FileEditorManagerListener getFileEditorManagerListener(@NotNull final Project project, @NotNull final StudyToolWindow toolWindow);
    
  @NotNull String getDefaultHighlightingMode();
  
  boolean accept(@NotNull final Project project);
  
}
