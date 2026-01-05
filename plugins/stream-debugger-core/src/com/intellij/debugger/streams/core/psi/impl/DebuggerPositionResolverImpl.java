// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.psi.impl;

import com.intellij.debugger.streams.core.psi.DebuggerPositionResolver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebuggerPositionResolverImpl implements DebuggerPositionResolver {
  private static final Logger logger = Logger.getInstance(DebuggerPositionResolverImpl.class);

  @Override
  public @Nullable PsiElement getNearestElementToBreakpoint(@NotNull XDebugSession session) {
    final XSourcePosition position = session.getCurrentPosition();
    logger.debug("Current position: ", position);

    if (position == null) return null;

    int offset = position.getOffset();
    final VirtualFile file = position.getFile();
    if (file.isValid() && 0 <= offset && offset < file.getLength()) {
      @Nullable PsiFile psiFile = PsiManager.getInstance(session.getProject()).findFile(file);
      if (logger.isDebugEnabled())
        logger.debug("Psi file: ", (psiFile != null ? psiFile.getName() : null));

      PsiElement element = psiFile != null && psiFile.isValid() ? psiFile.findElementAt(offset) : null;
      if (logger.isDebugEnabled())
        logger.debug("Element: ", (element != null ? element.getText() + "(" + element.getTextRange() + ")" : null));

      return element;
    }

    if (logger.isDebugEnabled())
      logger.debug("Offset out of bounds. isValid: ", file.isValid(), ", offset: ", offset, ", length: ", file.getLength());
    return null;
  }
}
