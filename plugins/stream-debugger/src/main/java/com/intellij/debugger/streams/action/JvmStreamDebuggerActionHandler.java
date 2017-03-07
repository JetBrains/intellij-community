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
import com.intellij.debugger.streams.resolve.ResolvedCallImpl;
import com.intellij.debugger.streams.resolve.ResolverFactoryImpl;
import com.intellij.debugger.streams.resolve.ValuesOrderResolver;
import com.intellij.debugger.streams.trace.MapStreamTracerImpl;
import com.intellij.debugger.streams.trace.TracingCallback;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.ui.TraceWindow;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
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

import java.util.ArrayList;
import java.util.Collections;
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
      final StreamChain chain = StreamChainBuilder.tryBuildChain(elementAtCursor);
      if (chain != null) {
        handle(session, chain);
      }
    }
  }

  public void handle(@NotNull XDebugSession session, @NotNull StreamChain chain) {
    new MapStreamTracerImpl(session).trace(chain, new TracingCallback() {
      @Override
      public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
        final List<ResolvedCall> calls = resolve(chain, result);
        ApplicationManager.getApplication()
          .invokeLater(() -> new TraceWindow(context, session.getProject(), calls).show());
      }

      @Override
      public void failed(@NotNull String traceExpression, @NotNull String reason) {
        LOG.warn(reason + System.lineSeparator() + "expression:" + System.lineSeparator() + traceExpression);
      }
    });
  }

  public boolean isEnabled(@NotNull Project project) {
    final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    final PsiElement elementAtCursor = session == null ? null : findElementAtCursor(session);
    return elementAtCursor != null && StreamChainBuilder.checkStreamExists(elementAtCursor);
  }

  protected static List<ResolvedCall> resolve(@NotNull StreamChain chain, @NotNull TracingResult tracingResult) {
    if (chain.length() == 0) {
      return Collections.emptyList();
    }

    final List<ValuesOrderResolver.Result> resolvedMappings = new ArrayList<>();
    final List<TraceInfo> trace = tracingResult.getTrace();
    for (int i = 0, length = chain.length(); i < length; i++) {
      final TraceInfo info = trace.get(i);
      final StreamCall call = chain.getCall(i);
      final String callName = call.getName();

      final ValuesOrderResolver resolver = ResolverFactoryImpl.getInstance().getResolver(callName);
      final ValuesOrderResolver.Result resolveResult = resolver.resolve(info);
      resolvedMappings.add(resolveResult);
    }

    final List<ResolvedCall> result = new ArrayList<>();

    final ResolvedCall sourceCall = new ResolvedCallImpl(chain.getCall(0), Collections.emptyMap(),
                                                         resolvedMappings.get(0).getDirectOrder());
    result.add(sourceCall);
    for (int i = 1; i < chain.length(); i++) {
      final StreamCall call = chain.getCall(i);
      final ValuesOrderResolver.Result prev = resolvedMappings.get(i - 1);
      final ValuesOrderResolver.Result current = resolvedMappings.get(i);
      result.add(new ResolvedCallImpl(call, current.getReverseOrder(), prev.getDirectOrder()));
    }

    return result;
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
