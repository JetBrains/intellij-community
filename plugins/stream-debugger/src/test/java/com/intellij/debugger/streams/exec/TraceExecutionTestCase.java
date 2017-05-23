/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.OutputChecker;
import com.intellij.debugger.streams.psi.DebuggerPositionResolver;
import com.intellij.debugger.streams.psi.impl.AdvancedStreamChainBuilder;
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl;
import com.intellij.debugger.streams.psi.impl.StreamChainTransformerImpl;
import com.intellij.debugger.streams.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.resolve.ResolvedStreamChain;
import com.intellij.debugger.streams.trace.*;
import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl;
import com.intellij.debugger.streams.trace.impl.TraceResultInterpreterImpl;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.Value;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class TraceExecutionTestCase extends DebuggerTestCase {
  private static final ChainSelector DEFAULT_CHAIN_SELECTOR = ChainSelector.byIndex(0);
  private final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();
  private final TraceResultInterpreter myResultInterpreter = new TraceResultInterpreterImpl();
  private final StreamChainBuilder myChainBuilder = new AdvancedStreamChainBuilder(new StreamChainTransformerImpl());

  @Override
  protected OutputChecker initOutputChecker() {
    return new OutputChecker(getTestAppPath(), getAppOutputPath());
  }

  @Override
  protected String getTestAppPath() {
    return new File("testData/debug/").getAbsolutePath();
  }

  protected void doTest(boolean isResultNull) {
    doTest(isResultNull, DEFAULT_CHAIN_SELECTOR);
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
    throws InterruptedException, ExecutionException, InvocationTargetException {
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
          t.printStackTrace();
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
    return myResultInterpreter;
  }

  @SuppressWarnings("WeakerAccess")
  protected StreamChainBuilder getChainBuilder() {
    return myChainBuilder;
  }

  @SuppressWarnings("WeakerAccess")
  protected TraceExpressionBuilder getExpressionBuilder() {
    return new TraceExpressionBuilderImpl(getProject());
  }

  protected void handleError(@NotNull StreamChain chain, @NotNull String error, @NotNull FailureReason reason) {
    fail();
  }

  protected void handleSuccess(@Nullable StreamChain chain,
                               @Nullable TracingResult result,
                               boolean resultMustBeNull) {
    assertNotNull(chain);
    assertNotNull(result);

    println(chain.getText(), ProcessOutputTypes.SYSTEM);

    final Value resultValue = result.getResult();
    handleResultValue(resultValue, resultMustBeNull);

    final List<TraceInfo> trace = result.getTrace();
    handleTrace(trace);

    final ResolvedTracingResult resolvedTrace = result.resolve();
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

    final ResolvedStreamCall.Producer producer = resolvedChain.getProducer();
    final ResolvedStreamCall.Terminator terminator = resolvedChain.getTerminator();
    printBeforeAndAfterValues(producer.getStateBefore(), producer.getStateAfter());
    printBeforeAndAfterValues(terminator.getStateBefore(), terminator.getStateAfter());

    resolvedChain.getIntermediateCalls().forEach(x -> printBeforeAndAfterValues(x.getStateBefore(), x.getStateAfter()));
  }

  private void printBeforeAndAfterValues(@Nullable NextAwareState before, @Nullable PrevAwareState after) {
    assertFalse(before == null && after == null);
    final StreamCall call = before == null ? after.getPrevCall() : before.getNextCall();
    println("mappings for " + call.getName(), ProcessOutputTypes.SYSTEM);
    println("  direct:", ProcessOutputTypes.SYSTEM);
    if (before != null) {
      printMapping(before.getTrace(), before::getNextValues, Direction.FORWARD);
    }
    else {
      println("    not found", ProcessOutputTypes.SYSTEM);
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
                            @NotNull Function<TraceElement, List<TraceElement>> mapper,
                            @NotNull Direction direction) {
    for (final TraceElement element : values) {
      final List<TraceElement> mappedValues = mapper.apply(element);
      final String mapped = traceToString(mappedValues);
      final String line = Direction.FORWARD.equals(direction) ? element.getTime() + " -> " + mapped : mapped + " -> " + element.getTime();
      println("    " + line, ProcessOutputTypes.SYSTEM);
    }
  }

  private enum Direction {
    FORWARD, BACKWARD
  }

  private static void checkChain(@NotNull ResolvedStreamChain chain) {
    final ResolvedStreamCall.Producer producer = chain.getProducer();
    final NextAwareState before = producer.getStateBefore();
    if (before != null) {
      assertEquals(before.getNextCall().getName(), producer.getCall().getName());
    }

    assertEquals(producer.getCall(), producer.getStateAfter().getPrevCall());
    final List<ResolvedStreamCall.Intermediate> intermediates = chain.getIntermediateCalls();
    final ResolvedStreamCall.Terminator terminator = chain.getTerminator();
    if (intermediates.isEmpty()) {
      assertEquals(producer.getCall().getName(), terminator.getStateBefore().getPrevCall().getName());
    }

    checkIntermediates(chain.getIntermediateCalls());

    assertEquals(terminator.getCall().getName(), terminator.getStateBefore().getNextCall().getName());
    final PrevAwareState after = terminator.getStateAfter();
    if (after != null) {
      assertEquals(terminator.getCall().getName(), after.getPrevCall().getName());
    }

    if (!intermediates.isEmpty()) {
      assertEquals(terminator.getCall().getName(), intermediates.get(intermediates.size() - 1).getStateAfter().getNextCall().getName());
    }
  }

  private static void checkIntermediates(@NotNull List<ResolvedStreamCall.Intermediate> intermediates) {
    for (int i = 0; i < intermediates.size() - 1; i++) {
      final ResolvedStreamCall.Intermediate prev = intermediates.get(i);
      final ResolvedStreamCall.Intermediate next = intermediates.get(i + 1);
      assertSame(prev.getStateAfter(), next.getStateBefore());
      assertEquals(prev.getCall().getName(), prev.getStateAfter().getPrevCall().getName());
      assertEquals(next.getCall().getName(), next.getStateBefore().getNextCall().getName());
    }
  }

  private static void checkTracesIsCorrectInBothDirections(@NotNull ResolvedStreamChain resolvedChain) {
    final ResolvedStreamCall.Producer producer = resolvedChain.getProducer();
    final NextAwareState before = producer.getStateBefore();
    if (before != null) {
      checkNeighborTraces(before, producer.getStateAfter());
    }

    final ResolvedStreamCall.Terminator terminator = resolvedChain.getTerminator();
    final PrevAwareState after = terminator.getStateAfter();
    if (after != null) {
      checkNeighborTraces(terminator.getStateBefore(), after);
    }

    final List<ResolvedStreamCall.Intermediate> intermediates = resolvedChain.getIntermediateCalls();
    if (intermediates.isEmpty()) {
      checkNeighborTraces(producer.getStateAfter(), terminator.getStateBefore());
    }
    else {
      for (final ResolvedStreamCall.Intermediate intermediate : intermediates) {
        checkNeighborTraces(intermediate.getStateBefore(), intermediate.getStateAfter());
      }
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
                                                 @NotNull Function<TraceElement, List<TraceElement>> toNext,
                                                 @NotNull Function<TraceElement, List<TraceElement>> toPrev) {
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
