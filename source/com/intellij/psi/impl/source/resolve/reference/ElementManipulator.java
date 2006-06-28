package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.04.2003
 * Time: 11:22:05
 * To change this template use Options | File Templates.
 */
public interface ElementManipulator<T extends PsiElement> {

  T handleContentChange(T element, TextRange range, String newContent) throws IncorrectOperationException;

  T handleContentChange(T element, String newContent) throws IncorrectOperationException;

  TextRange getRangeInElement(T element);
}
