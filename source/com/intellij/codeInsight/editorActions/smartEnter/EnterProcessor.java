package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 10:53:40 PM
 * To change this template use Options | File Templates.
 */
public interface EnterProcessor {
  boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified);
}
