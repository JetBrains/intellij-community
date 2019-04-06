// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author Vitaliy.Bibaev
 */
public class DebuggerPositionResolverImpl implements DebuggerPositionResolver {
  @Nullable
  @Override
  public PsiElement getNearestElementToBreakpoint(@NotNull XDebugSession session) {
    final XSourcePosition position = session.getCurrentPosition();
    if (position == null) return null;

    int offset = position.getOffset();
    final VirtualFile file = position.getFile();
    if (0 <= offset && offset < file.getLength()) {
      return DebuggerUtilsEx.findElementAt(PsiManager.getInstance(session.getProject()).findFile(file), offset);
    }

    return null;
  }
}
