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
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl;
import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.trace.*;
import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl;
import com.intellij.debugger.streams.trace.impl.TraceResultInterpreterImpl;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.debugger.streams.wrapper.impl.StreamChainBuilderImpl;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class TraceExecutionTestCase extends DebuggerTestCase {
  private final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();
  private final TraceResultInterpreter myResultInterpreter = new TraceResultInterpreterImpl();
  private final StreamChainBuilder myChainBuilder = new StreamChainBuilderImpl();

  @Override
  protected OutputChecker initOutputChecker() {
    return new OutputChecker(getTestAppPath(), getAppOutputPath());
  }

  @Override
  protected String getTestAppPath() {
    return new File("testData/debug/").getAbsolutePath();
  }

  protected void doTest(boolean isResultNull) throws InterruptedException, ExecutionException, InvocationTargetException {
    final String className = getTestName(false);

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
          final List<StreamChain> chains = elementAtBreakpoint == null ? null : myChainBuilder.build(elementAtBreakpoint);
          return chains == null || chains.isEmpty() ? null : chains.get(0);
        });

        if (chain == null) {
          complete(null, null, null, FailureReason.CHAIN_CONTRUCTION);
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
          println("Exception caught: " + t, ProcessOutputTypes.SYSTEM);
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
      println(valuesOrderToString(before), ProcessOutputTypes.SYSTEM);

      print("    after: ", ProcessOutputTypes.SYSTEM);
      final Map<Integer, TraceElement> after = info.getValuesOrderAfter();
      println(valuesOrderToString(after), ProcessOutputTypes.SYSTEM);
    }
  }

  @SuppressWarnings("WeakerAccess")
  protected void handleResolvedTrace(@NotNull ResolvedTracingResult result) {
    final List<ResolvedTrace> traces = result.getResolvedTraces();

    checkTracesIsCorrectInBothDirections(traces);

    for (final ResolvedTrace trace : traces) {
      final String name = trace.getCall().getName();
      final List<TraceElement> values = trace.getValues();

      println("mappings for " + name, ProcessOutputTypes.SYSTEM);

      for (final TraceElement element : StreamEx.of(values).sortedBy(TraceElement::getTime)) {
        final String beforeTimes = StreamEx.of(trace.getPreviousValues(element)).map(TraceElement::getTime).sorted().joining(", ");
        final String afterTimes = StreamEx.of(trace.getNextValues(element)).map(TraceElement::getTime).sorted().joining(",");
        println(evalTimesRepresentation(beforeTimes, element.getTime(), afterTimes), ProcessOutputTypes.SYSTEM);
      }
    }
  }

  @NotNull
  private static String evalTimesRepresentation(@NotNull String before, int elementTime, @NotNull String after) {
    before = replaceIfEmpty(before);
    after = replaceIfEmpty(after);
    return String.format("    %s -> %d -> %s", before, elementTime, after);
  }

  private static void checkTracesIsCorrectInBothDirections(@NotNull List<ResolvedTrace> resolvedTraces) {
    for (int i = 1, size = resolvedTraces.size(); i < size; i++) {
      final ResolvedTrace previous = resolvedTraces.get(i - 1);
      final ResolvedTrace current = resolvedTraces.get(i);
      checkNeighborTraces(previous, current);
    }
  }

  private static void checkNeighborTraces(@NotNull ResolvedTrace left, @NotNull ResolvedTrace right) {
    final Set<TraceElement> leftValues = new HashSet<>(left.getValues());
    final Set<TraceElement> rightValues = new HashSet<>(right.getValues());

    checkThatMappingsIsCorrect(leftValues, rightValues, left::getNextValues, right::getPreviousValues);
    checkThatMappingsIsCorrect(rightValues, leftValues, right::getPreviousValues, left::getNextValues);
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
  private static String valuesOrderToString(@NotNull Map<Integer, TraceElement> values) {
    return replaceIfEmpty(StreamEx.of(values.keySet()).sorted().joining(","));
  }

  @NotNull
  private static String replaceIfEmpty(@NotNull String str) {
    return str.isEmpty() ? "nothing" : str;
  }

  protected enum FailureReason {
    COMPILATION, EVALUATION, CHAIN_CONTRUCTION
  }
}
