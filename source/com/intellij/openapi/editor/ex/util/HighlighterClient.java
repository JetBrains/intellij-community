package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public interface HighlighterClient {
  Project getProject();

  void repaint(int start, int end);

  Document getDocument();
}
