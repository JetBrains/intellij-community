package com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   18:38:10
 */
public interface EnterProcessor {
  boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified);
}
