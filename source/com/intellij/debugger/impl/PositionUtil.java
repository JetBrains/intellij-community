package com.intellij.debugger.impl;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.Computable;
/**
 * User: lex
 * Date: Oct 29, 2003
 * Time: 9:29:36 PM
 */
public class PositionUtil extends ContextUtil {
  public static SourcePosition getSourcePosition(final StackFrameContext context) {
    if(context instanceof DebuggerContextImpl) return ((DebuggerContextImpl)context).getSourcePosition();

    return ContextUtil.getSourcePosition(context);
  }

  public static PsiElement getContextElement(final StackFrameContext context) {
    if(context instanceof DebuggerContextImpl) return ((DebuggerContextImpl) context).getContextElement();

    return ContextUtil.getContextElement(context);
  }
}
