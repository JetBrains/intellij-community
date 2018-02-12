// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.action;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.diagnostic.ex.TraceCompilationException;
import com.intellij.debugger.streams.diagnostic.ex.TraceEvaluationException;
import com.intellij.debugger.streams.lib.LibrarySupport;
import com.intellij.debugger.streams.lib.LibrarySupportProvider;
import com.intellij.debugger.streams.psi.DebuggerPositionResolver;
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl;
import com.intellij.debugger.streams.trace.*;
import com.intellij.debugger.streams.trace.impl.TraceResultInterpreterImpl;
import com.intellij.debugger.streams.ui.ChooserOption;
import com.intellij.debugger.streams.ui.impl.ElementChooserImpl;
import com.intellij.debugger.streams.ui.impl.EvaluationAwareTraceWindow;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceStreamAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(TraceStreamAction.class);

  private final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();
  private final List<SupportedLibrary> mySupportedLibraries =
    LibrarySupportProvider.getList().stream().map(SupportedLibrary::new).collect(Collectors.toList());
  private final Set<String> mySupportedLanguages = StreamEx.of(mySupportedLibraries).map(x -> x.languageId).toSet();
  private int myLastVisitedPsiElementHash;

  @Override
  public void update(@NotNull AnActionEvent e) {
    final XDebugSession session = getCurrentSession(e);
    final PsiElement element = session == null ? null : myPositionResolver.getNearestElementToBreakpoint(session);
    final Presentation presentation = e.getPresentation();
    if (element == null) {
      presentation.setVisible(true);
      presentation.setEnabled(false);
    }
    else {
      final String languageId = element.getLanguage().getID();
      if (mySupportedLanguages.contains(languageId)) {
        presentation.setVisible(true);
        final boolean chainExists = isChainExists(element);
        presentation.setEnabled(chainExists);
        final int elementHash = System.identityHashCode(element);
        if (chainExists && myLastVisitedPsiElementHash != elementHash) {
          UsageTrigger.trigger("debugger.streams." + languageId.toLowerCase(Locale.US) + ".activated");
          myLastVisitedPsiElementHash = elementHash;
        }
      }
      else {
        presentation.setEnabledAndVisible(false);
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final XDebugSession session = getCurrentSession(e);
    Extensions.getExtensions(LibrarySupportProvider.EP_NAME);
    final PsiElement element = session == null ? null : myPositionResolver.getNearestElementToBreakpoint(session);

    if (element != null && isJdkAtLeast9(session.getProject(), element)) {
      XDebuggerManagerImpl.NOTIFICATION_GROUP
        .createNotification("This action does not work with JDK 9 yet", MessageType.WARNING)
        .notify(session.getProject());
      return;
    }

    if (element != null) {
      final List<StreamChainWithLibrary> chains = mySupportedLibraries.stream()
        .filter(library -> library.languageId.equals(element.getLanguage().getID()))
        .filter(library -> library.builder.isChainExists(element))
        .flatMap(library -> library.builder.build(element).stream().map(x -> new StreamChainWithLibrary(x, library)))
        .collect(Collectors.toList());
      if (chains.isEmpty()) {
        LOG.warn("stream chain is not built");
        return;
      }

      if (chains.size() == 1) {
        runTrace(chains.get(0).chain, chains.get(0).library, session);
      }
      else {
        final Editor editor = PsiEditorUtil.Service.getInstance().findEditorByPsiElement(element);
        if (editor == null) {
          throw new RuntimeException("editor not found");
        }

        new MyStreamChainChooser(editor).show(chains.stream().map(StreamChainOption::new).collect(Collectors.toList()),
                                              provider -> runTrace(provider.chain, provider.library, session));
      }
    }
    else {
      LOG.info("element at cursor not found");
    }
  }

  private boolean isChainExists(@NotNull PsiElement element) {
    for (final SupportedLibrary library : mySupportedLibraries) {
      if (element.getLanguage().getID().equals(library.languageId) && library.builder.isChainExists(element)) {
        return true;
      }
    }

    return false;
  }

  private static void runTrace(@NotNull StreamChain chain, @NotNull SupportedLibrary library, @NotNull XDebugSession session) {
    final EvaluationAwareTraceWindow window = new EvaluationAwareTraceWindow(session, chain);
    UsageTrigger.trigger("debugger.streams." + library.languageId.toLowerCase(Locale.US) + ".used");
    ApplicationManager.getApplication().invokeLater(window::show);
    final Project project = session.getProject();
    final TraceExpressionBuilder expressionBuilder = library.createExpressionBuilder(project);
    final TraceResultInterpreterImpl resultInterpreter = new TraceResultInterpreterImpl(library.librarySupport.getInterpreterFactory());
    final StreamTracer tracer = new EvaluateExpressionTracer(session, expressionBuilder, resultInterpreter);
    tracer.trace(chain, new TracingCallback() {
      @Override
      public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
        final ResolvedTracingResult resolvedTrace = result.resolve(library.librarySupport.getResolverFactory());
        ApplicationManager.getApplication()
          .invokeLater(() -> window.setTrace(resolvedTrace, context));
      }

      @Override
      public void evaluationFailed(@NotNull String traceExpression, @NotNull String message) {
        notifyUI(message);
        throw new TraceEvaluationException(message, traceExpression);
      }

      @Override
      public void compilationFailed(@NotNull String traceExpression, @NotNull String message) {
        notifyUI(message);
        throw new TraceCompilationException(message, traceExpression);
      }

      private void notifyUI(@NotNull String message) {
        ApplicationManager.getApplication().invokeLater(() -> window.setFailMessage(message));
      }
    });
  }

  @Nullable
  private static XDebugSession getCurrentSession(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    return project == null ? null : XDebuggerManager.getInstance(project).getCurrentSession();
  }

  private static boolean isJdkAtLeast9(@NotNull Project project, @NotNull PsiElement element) {
    if (element.getLanguage().is(JavaLanguage.INSTANCE)) {
      final Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null) {
        final JavaSdkVersion javaVersion = JavaSdk.getInstance().getVersion(sdk);
        if (javaVersion != null) return javaVersion.isAtLeast(JavaSdkVersion.JDK_1_9);
      }
    }

    return false;
  }

  private static class MyStreamChainChooser extends ElementChooserImpl<StreamChainOption> {
    MyStreamChainChooser(@NotNull Editor editor) {
      super(editor);
    }
  }

  private static class SupportedLibrary {
    final String languageId;
    final StreamChainBuilder builder;
    final LibrarySupport librarySupport;
    private final LibrarySupportProvider mySupportProvider;

    SupportedLibrary(@NotNull LibrarySupportProvider provider) {
      languageId = provider.getLanguageId();
      builder = provider.getChainBuilder();
      librarySupport = provider.getLibrarySupport();
      mySupportProvider = provider;
    }

    TraceExpressionBuilder createExpressionBuilder(@NotNull Project project) {
      return mySupportProvider.getExpressionBuilder(project);
    }
  }

  private static class StreamChainWithLibrary {
    final StreamChain chain;
    final SupportedLibrary library;

    StreamChainWithLibrary(@NotNull StreamChain chain, @NotNull SupportedLibrary library) {
      this.chain = chain;
      this.library = library;
    }
  }

  private static class StreamChainOption implements ChooserOption {
    final StreamChain chain;
    final SupportedLibrary library;

    StreamChainOption(@NotNull StreamChainWithLibrary chain) {
      this.chain = chain.chain;
      library = chain.library;
    }

    @NotNull
    @Override
    public Stream<TextRange> rangeStream() {
      return Stream.of(
        new TextRange(chain.getQualifierExpression().getTextRange().getStartOffset(),
                      chain.getTerminationCall().getTextRange().getEndOffset()));
    }

    @NotNull
    @Override
    public String getText() {
      return chain.getCompactText();
    }
  }
}
