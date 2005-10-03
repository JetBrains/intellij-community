package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

public interface ExpressionContext {
  @NonNls String SELECTION = "SELECTION";

  Project getProject();
  Editor getEditor();
  int getStartOffset();
  int getTemplateStartOffset();
  int getTemplateEndOffset();
  Map getProperties();
}

