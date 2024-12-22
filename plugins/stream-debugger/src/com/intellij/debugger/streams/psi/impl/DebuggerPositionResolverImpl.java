// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.psi.DebuggerPositionResolver;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebuggerPositionResolverImpl implements DebuggerPositionResolver {
  @Override
  public @Nullable PsiElement getNearestElementToBreakpoint(@NotNull XDebugSession session) {
    final XSourcePosition position = session.getCurrentPosition();
    if (position == null) return null;

    int offset = position.getOffset();
    final VirtualFile file = position.getFile();
    if (file.isValid() && 0 <= offset && offset < file.getLength()) {
      return DebuggerUtilsEx.findElementAt(PsiManager.getInstance(session.getProject()).findFile(file), offset);
    }

    return null;
  }
}
