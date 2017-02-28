/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.action;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.resolve.ResolvedCall;
import com.intellij.debugger.streams.trace.MapStreamTracerImpl;
import com.intellij.debugger.streams.trace.TracingCallback;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.ui.TraceWindow;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class JvmStreamDebuggerActionHandler {
  private static final Logger LOG = Logger.getInstance(JvmStreamDebuggerActionHandler.class);

  public void perform(@NotNull Project project, AnActionEvent event) {
    final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) {
      return;
    }

    final PsiElement elementAtCursor = findElementAtCursor(session);
    if (elementAtCursor != null) {
      final StreamChain chain = StreamChain.tryBuildChain(elementAtCursor);
      if (chain != null) {
        handle(session, chain);
      }
    }
  }

  public void handle(@NotNull XDebugSession session, @NotNull StreamChain chain) {
    new MapStreamTracerImpl(session).trace(chain, new TracingCallback() {
      @Override
      public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
        final List<ResolvedCall> calls = chain.resolveCalls(result);
        ApplicationManager.getApplication()
          .invokeLater(() -> new TraceWindow(context, session.getProject(), calls).show());
      }

      @Override
      public void failed(@NotNull String traceExpression, @NotNull String reason) {
        LOG.warn(reason + System.lineSeparator() + "expression:" + System.lineSeparator() + traceExpression);
      }
    });
  }

  public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
    final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    final PsiElement elementAtCursor = session == null ? null : findElementAtCursor(session);
    return elementAtCursor != null && StreamChain.checkStreamExists(elementAtCursor);
  }

  @Nullable
  private static PsiElement findElementAtCursor(@NotNull XDebugSession session) {
    final Project project = session.getProject();
    final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(session.getProject()).getContext();
    final SourcePosition position = debuggerContext.getSourcePosition();

    if (position == null) {
      return null;
    }

    final int line = position.getLine();
    final PsiFile psiFile = position.getFile();
    final VirtualFile file = psiFile.getVirtualFile();

    if (file != null) {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        final int offset = document.getLineStartOffset(line);
        return DebuggerUtilsEx.findElementAt(PsiDocumentManager.getInstance(project).getPsiFile(document), offset);
      }
    }

    return null;
  }
}
