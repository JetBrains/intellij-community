package com.jetbrains.python.psi;

/**
 * Used for literal None and literal ... (Ellipsis).
 * 
 * @author yole
 */
public interface PyNoneLiteralExpression extends PyLiteralExpression {
  boolean isEllipsis();
}
