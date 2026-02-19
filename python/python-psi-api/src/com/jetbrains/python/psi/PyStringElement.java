package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.ast.PyAstStringElement;

/**
 * A common interface containing utility methods shared among both plain string literals
 * (i.e. those that don't have "f" prefix) and formatted literals (f-strings).
 *
 * @see PyPlainStringElement
 * @see PyFormattedStringElement
 */
public interface PyStringElement extends PyAstStringElement, PsiElement {
}
