package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public interface PyBreakpointType {
  boolean canPutInDocument(Project project, Document document);
}
