// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.test;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.OutputChecker;
import com.intellij.debugger.streams.lib.LibrarySupportProvider;
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupportProvider;
import com.intellij.debugger.streams.psi.DebuggerPositionResolver;
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl;
import com.intellij.debugger.streams.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.resolve.ResolvedStreamChain;
import com.intellij.debugger.streams.trace.*;
import com.intellij.debugger.streams.trace.impl.TraceResultInterpreterImpl;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.Value;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * @author Vitaliy.Bibaev
 */
@SkipSlowTestLocally
public abstract class TraceExecutionTestCase extends DebuggerTestCase {
  private static final ChainSelector DEFAULT_CHAIN_SELECTOR = ChainSelector.byIndex(0);
  private static final LibrarySupportProvider DEFAULT_LIBRARY_SUPPORT_PROVIDER = new StandardLibrarySupportProvider();
  private final Logger LOG = Logger.getInstance(getClass());
  private final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();

  @Override
  protected OutputChecker initOutputChecker() {
    return new OutputChecker(() -> getTestAppPath(), () -> getAppOutputPath()) {
      @Override
      protected String replaceAdditionalInOutput(String str) {
        return TraceExecutionTestCase.this.replaceAdditionalInOutput(super.replaceAdditionalInOutput(str));
      }
    };
  }

  @NotNull
  protected String replaceAdditionalInOutput(@NotNull String str) {
    return str;
  }

  protected LibrarySupportProvider getLibrarySupportProvider() {
    return DEFAULT_LIBRARY_SUPPORT_PROVIDER;
  }

  @Override
  protected String getTestAppPath() {
    return new File(PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/debug/").getAbsolutePath();
  }
  
  @Override
  protected void tearDown() throws Exception {
    try {
      //noinspection SuperTearDownInFinally
      super.tearDown();
    }
    catch (Throwable t) {
      if (!t.getMessage().startsWith("Thread leaked: Thread[")) {
        throw t;
      }
    }
  }

  protected void doTest(boolean isResultNull) {
    doTest(isResultNull, DEFAULT_CHAIN_SELECTOR);
  }

  protected void doTest(boolean isResultNull, @NotNull String className) {
    doTest(isResultNull, className, DEFAULT_CHAIN_SELECTOR);
  }

  protected void doTest(boolean isResultNull, @NotNull ChainSelector chainSelector) {
    final String className = getTestName(false);
    doTest(isResultNull, className, chainSelector);
  }

  protected void doTest(boolean isResultNull, @NotNull String className, @NotNull ChainSelector chainSelector) {
    try {
      doTestImpl(isResultNull, className, chainSelector);
    }
    catch (Exception e) {
      throw new AssertionError("exception thrown", e);
    }
  }

  private void doTestImpl(boolean isResultNull, @NotNull String className, @NotNull ChainSelector chainSelector)
    throws ExecutionException {
    LOG.info("Test started: " + getTestName(false));
    createLocalProcess(className);
    final XDebugSession session = getDebuggerSession().getXDebugSession();
    assertNotNull(session);

    final AtomicBoolean completed = new AtomicBoolean(false);
    final DebuggerPositionResolver positionResolver = getPositionResolver();
    final StreamChainBuilder chainBuilder = getChainBuilder();
    final TraceResultInterpreter resultInterpreter = getResultInterpreter();
    final TraceExpressionBuilder expressionBuilder = getExpressionBuilder();

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        if (completed.getAndSet(true)) {
          resume();
          return;
        }
        try {
          sessionPausedImpl();
        }
        catch (Throwable t) {
          println("Exception caught: " + t + ", " + t.getMessage(), ProcessOutputTypes.SYSTEM);

          //noinspection CallToPrintStackTrace
          t.printStackTrace();

          resume();
        }
      }

      private void sessionPausedImpl() {
        printContext(getDebugProcess().getDebuggerContext());
        final StreamChain chain = ApplicationManager.getApplication().runReadAction((Computable<StreamChain>)() -> {
          final PsiElement elementAtBreakpoint = positionResolver.getNearestElementToBreakpoint(session);
          final List<StreamChain> chains = elementAtBreakpoint == null ? null : chainBuilder.build(elementAtBreakpoint);
          return chains == null || chains.isEmpty() ? null : chainSelector.select(chains);
        });

        if (chain == null) {
          complete(null, null, null, FailureReason.CHAIN_CONSTRUCTION);
          return;
        }

        new EvaluateExpressionTracer(session, expressionBuilder, resultInterpreter).trace(chain, new TracingCallback() {
          @Override
          public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
            complete(chain, result, null, null);
          }

          @Override
          public void evaluationFailed(@NotNull String traceExpression, @NotNull String message) {
            complete(chain, null, message, FailureReason.EVALUATION);
          }

          @Override
          public void compilationFailed(@NotNull String traceExpression, @NotNull String message) {
            LOG.warn("[" + getTestName(false) + "] Compilation failed.");
            complete(chain, null, message, FailureReason.COMPILATION);
          }
        });
      }

      private void complete(@Nullable StreamChain chain,
                            @Nullable TracingResult result,
                            @Nullable String error,
                            @Nullable FailureReason errorReason) {
        try {
          if (error != null) {
            assertNotNull(errorReason);
            assertNotNull(chain);
            handleError(chain, error, errorReason);
          }
          else {
            assertNull(errorReason);
            handleSuccess(chain, result, isResultNull);
          }
        }
        catch (Throwable t) {
          println("Exception caught: " + t + ", " + t.getMessage(), ProcessOutputTypes.SYSTEM);
        }
        finally {
          resume();
        }
      }

      private void resume() {
        ApplicationManager.getApplication().invokeLater(session::resume);
      }
    }, getTestRootDisposable());
  }

  @SuppressWarnings("WeakerAccess")
  protected DebuggerPositionResolver getPositionResolver() {
    return myPositionResolver;
  }

  @SuppressWarnings("WeakerAccess")
  protected TraceResultInterpreter getResultInterpreter() {
    return new TraceResultInterpreterImpl(getLibrarySupportProvider().getLibrarySupport().getInterpreterFactory());
  }

  @SuppressWarnings("WeakerAccess")
  protected StreamChainBuilder getChainBuilder() {
    return getLibrarySupportProvider().getChainBuilder();
  }

  @SuppressWarnings("WeakerAccess")
  protected TraceExpressionBuilder getExpressionBuilder() {
    return getLibrarySupportProvider().getExpressionBuilder(getProject());
  }

  protected void handleError(@NotNull StreamChain chain, @NotNull String error, @NotNull FailureReason reason) {
    fail(error);
  }

  protected void handleSuccess(@Nullable StreamChain chain,
                               @Nullable TracingResult result,
                               boolean resultMustBeNull) {
    assertNotNull(chain);
    assertNotNull(result);

    println(chain.getText(), ProcessOutputTypes.SYSTEM);

    final TraceElement resultValue = result.getResult();
    handleResultValue(resultValue.getValue(), resultMustBeNull);

    final List<TraceInfo> trace = result.getTrace();
    handleTrace(trace);

    final ResolvedTracingResult resolvedTrace = result.resolve(getLibrarySupportProvider().getLibrarySupport().getResolverFactory());
    handleResolvedTrace(resolvedTrace);
  }

  protected void handleResultValue(@Nullable Value result, boolean mustBeNull) {
    if (mustBeNull) {
      assertNull(result);
    }
    else {
      assertNotNull(result);
    }
  }

  @SuppressWarnings("WeakerAccess")
  protected void handleTrace(@NotNull List<TraceInfo> trace) {
    for (final TraceInfo info : trace) {
      final String name = info.getCall().getName();
      println(name, ProcessOutputTypes.SYSTEM);

      print("    before: ", ProcessOutputTypes.SYSTEM);
      final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
      println(traceToString(before.values()), ProcessOutputTypes.SYSTEM);

      print("    after: ", ProcessOutputTypes.SYSTEM);
      final Map<Integer, TraceElement> after = info.getValuesOrderAfter();
      println(traceToString(after.values()), ProcessOutputTypes.SYSTEM);
    }
  }

  @SuppressWarnings("WeakerAccess")
  protected void handleResolvedTrace(@NotNull ResolvedTracingResult result) {
    final ResolvedStreamChain resolvedChain = result.getResolvedChain();

    checkChain(resolvedChain);
    checkTracesIsCorrectInBothDirections(resolvedChain);

    final ResolvedStreamCall.Terminator terminator = resolvedChain.getTerminator();
    resolvedChain.getIntermediateCalls().forEach(x -> printBeforeAndAfterValues(x.getStateBefore(), x.getStateAfter()));
    printBeforeAndAfterValues(terminator.getStateBefore(), terminator.getStateAfter());
  }

  private void printBeforeAndAfterValues(@Nullable NextAwareState before, @Nullable PrevAwareState after) {
    assertFalse(before == null && after == null);
    final StreamCall call = before == null ? after.getPrevCall() : before.getNextCall();
    assertNotNull(call);
    println("mappings for " + call.getName(), ProcessOutputTypes.SYSTEM);
    println("  direct:", ProcessOutputTypes.SYSTEM);
    if (before != null) {
      printMapping(before.getTrace(), before::getNextValues, Direction.FORWARD);
    }
    else {
      println("    no", ProcessOutputTypes.SYSTEM);
    }

    println("  reverse:", ProcessOutputTypes.SYSTEM);
    if (after != null) {
      printMapping(after.getTrace(), after::getPrevValues, Direction.BACKWARD);
    }
    else {
      println("    not found", ProcessOutputTypes.SYSTEM);
    }
  }

  private void printMapping(@NotNull List<TraceElement> values,
                            @NotNull Function<? super TraceElement, ? extends List<TraceElement>> mapper,
                            @NotNull Direction direction) {
    if (values.isEmpty()) {
      println("    empty", ProcessOutputTypes.SYSTEM);
    }
    for (final TraceElement element : values) {
      final List<TraceElement> mappedValues = mapper.apply(element);
      final String mapped = traceToString(mappedValues);
      final String line = Direction.FORWARD.equals(direction) ? element.getTime() + " -> " + mapped : mapped + " <- " + element.getTime();
      println("    " + line, ProcessOutputTypes.SYSTEM);
    }
  }

  private enum Direction {
    FORWARD, BACKWARD
  }

  private static void checkChain(@NotNull ResolvedStreamChain chain) {
    final List<ResolvedStreamCall.Intermediate> intermediates = chain.getIntermediateCalls();
    final ResolvedStreamCall.Terminator terminator = chain.getTerminator();
    if (intermediates.isEmpty()) {
      assertFalse(terminator.getStateBefore() instanceof PrevAwareState);
    }

    checkIntermediates(chain.getIntermediateCalls());

    assertEquals(terminator.getCall().getName(), terminator.getStateBefore().getNextCall().getName());
    final PrevAwareState after = terminator.getStateAfter();
    if (after != null) {
      final StreamCall terminatorCall = after.getPrevCall();
      assertNotNull(terminatorCall);
      assertEquals(terminator.getCall().getName(), terminatorCall.getName());
    }

    if (!intermediates.isEmpty()) {
      final ResolvedStreamCall.Intermediate lastIntermediate = intermediates.get(intermediates.size() - 1);
      final PrevAwareState stateAfterIntermediates = lastIntermediate.getStateAfter();
      assertInstanceOf(stateAfterIntermediates, NextAwareState.class);
      assertEquals(terminator.getCall().getName(), ((NextAwareState)stateAfterIntermediates).getNextCall().getName());
    }
  }

  private static void checkIntermediates(@NotNull List<ResolvedStreamCall.Intermediate> intermediates) {
    for (int i = 0; i < intermediates.size() - 1; i++) {
      final ResolvedStreamCall.Intermediate prev = intermediates.get(i);
      final ResolvedStreamCall.Intermediate next = intermediates.get(i + 1);
      assertSame(prev.getStateAfter(), next.getStateBefore());
      final StreamCall prevCall = prev.getStateAfter().getPrevCall();
      assertNotNull(prevCall);
      assertEquals(prev.getCall().getName(), prevCall.getName());
      assertEquals(next.getCall().getName(), next.getStateBefore().getNextCall().getName());
    }
  }

  private static void checkTracesIsCorrectInBothDirections(@NotNull ResolvedStreamChain resolvedChain) {
    for (final ResolvedStreamCall.Intermediate intermediate : resolvedChain.getIntermediateCalls()) {
      checkNeighborTraces(intermediate.getStateBefore(), intermediate.getStateAfter());
    }

    final ResolvedStreamCall.Terminator terminator = resolvedChain.getTerminator();
    final PrevAwareState after = terminator.getStateAfter();
    if (after != null) {
      checkNeighborTraces(terminator.getStateBefore(), after);
    }
  }

  private static void checkNeighborTraces(@NotNull NextAwareState left, @NotNull PrevAwareState right) {
    final Set<TraceElement> leftValues = new HashSet<>(left.getTrace());
    final Set<TraceElement> rightValues = new HashSet<>(right.getTrace());

    checkThatMappingsIsCorrect(leftValues, rightValues, left::getNextValues, right::getPrevValues);
    checkThatMappingsIsCorrect(rightValues, leftValues, right::getPrevValues, left::getNextValues);
  }

  private static void checkThatMappingsIsCorrect(@NotNull Set<TraceElement> prev,
                                                 @NotNull Set<TraceElement> next,
                                                 @NotNull Function<? super TraceElement, ? extends List<TraceElement>> toNext,
                                                 @NotNull Function<? super TraceElement, ? extends List<TraceElement>> toPrev) {
    for (final TraceElement leftElement : prev) {
      final List<TraceElement> mapToRight = toNext.apply(leftElement);
      for (final TraceElement rightElement : mapToRight) {
        assertTrue(next.contains(rightElement));
        assertTrue(toPrev.apply(rightElement).contains(leftElement));
      }
    }
  }

  @NotNull
  private static String traceToString(@NotNull Collection<TraceElement> trace) {
    return replaceIfEmpty(StreamEx.of(trace).map(TraceElement::getTime).sorted().joining(","));
  }

  @NotNull
  private static String replaceIfEmpty(@NotNull String str) {
    return str.isEmpty() ? "nothing" : str;
  }

  protected enum FailureReason {
    COMPILATION, EVALUATION, CHAIN_CONSTRUCTION
  }

  @FunctionalInterface
  protected interface ChainSelector {
    @NotNull
    StreamChain select(@NotNull List<StreamChain> chains);

    static ChainSelector byIndex(int index) {
      return chains -> chains.get(index);
    }
  }
}
