package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 07.01.2004
 * Time: 1:06:51
 * To change this template use Options | File Templates.
 */
public interface DataProvider {
  boolean hasSelection();
  int selectionStart();
  int selectionEnd();
  Editor getEditor();
  Project getProject();
}
