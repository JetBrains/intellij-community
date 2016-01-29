package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface StudyToolWindowConfigurator {
  ExtensionPointName<StudyToolWindowConfigurator> EP_NAME = ExtensionPointName.create("Edu.studyToolWindowConfigurator");
  
  @NotNull
  DefaultActionGroup getActionGroup(Project project);
  
  HashMap<String, JPanel> getAdditionalPanels(Project project);  
  
  boolean accept(@NotNull final Project project);
  
}
