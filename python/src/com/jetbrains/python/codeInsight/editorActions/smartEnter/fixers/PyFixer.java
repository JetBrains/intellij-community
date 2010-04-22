package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:10:33
 */
public interface PyFixer {
    void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException;
}
