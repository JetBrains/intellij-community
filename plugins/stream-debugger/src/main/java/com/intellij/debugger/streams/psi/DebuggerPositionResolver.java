package com.intellij.debugger.streams.psi;

import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public interface DebuggerPositionResolver {
  @Nullable
  PsiElement getNearestElementToBreakpoint(@NotNull XDebugSession session);
}
