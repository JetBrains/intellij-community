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
import com.intellij.debugger.streams.wrapper.impl.StreamChainBuilder;
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
public class TraceExecutionTestCase extends DebuggerTestCase {
  private final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();
  private final TraceExpressionBuilder myExpressionBuilder = new TraceExpressionBuilderImpl();
  private final TraceResultInterpreter myResultInterpreter = new TraceResultInterpreterImpl();

  public void testFilter() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testMap() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected OutputChecker initOutputChecker() {
    return new OutputChecker(getTestAppPath(), getAppOutputPath());
  }

  @Override
  protected String getTestAppPath() {
    return new File("testData/" + getRelativeTestPath()).getAbsolutePath();
  }

  protected void doTest(boolean isResultNull) throws InterruptedException, ExecutionException, InvocationTargetException {
    final String name = getTestName(false);

    createLocalProcess(name);
    final XDebugSession session = getDebuggerSession().getXDebugSession();
    assertNotNull(session);

    final AtomicBoolean completed = new AtomicBoolean(false);
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        if (completed.getAndSet(true)) {
          resume();
          return;
        }

        printContext(getDebugProcess().getDebuggerContext());
        final StreamChain chain = ApplicationManager.getApplication().runReadAction((Computable<StreamChain>)() -> {
          final PsiElement elementAtBreakpoint = myPositionResolver.getNearestElementToBreakpoint(session);
          return elementAtBreakpoint == null ? null : StreamChainBuilder.tryBuildChain(elementAtBreakpoint);
        });

        if (chain == null) {
          complete(null, null, null);
          return;
        }

        new EvaluateExpressionTracer(session, myExpressionBuilder, myResultInterpreter).trace(chain, new TracingCallback() {
          @Override
          public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
            complete(chain, result, null);
          }

          @Override
          public void failed(@NotNull String traceExpression, @NotNull String reason) {
            complete(chain, null, reason);
          }
        });
      }

      private void complete(@Nullable StreamChain chain,
                            @Nullable TracingResult result,
                            @Nullable String evaluationError) {
        handleResults(chain, result, evaluationError, isResultNull);
        resume();
      }

      private void resume() {
        ApplicationManager.getApplication().invokeLater(session::resume);
      }
    }, getTestRootDisposable());
  }

  protected void handleResults(@Nullable StreamChain chain,
                               @Nullable TracingResult result,
                               @Nullable String evaluationError,
                               boolean resultMustBeNull) {
    assertNotNull(chain);
    assertNull(evaluationError);
    assertNotNull(result);

    println(chain.getText(), ProcessOutputTypes.SYSTEM);

    final Value resultValue = result.getResult();
    handleResultValue(resultValue, resultMustBeNull);

    final List<TraceInfo> trace = result.getTrace();
    handleTrace(trace);

    final ResolvedTracingResult resolvedTrace = result.resolve();
    handleResultValue(resolvedTrace.getResult(), resultMustBeNull);
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

  protected void handleTrace(@NotNull List<TraceInfo> trace) {
    for (final TraceInfo info : trace) {
      final String name = info.getCall().getName();
      println(name, ProcessOutputTypes.SYSTEM);

      print("before: ", ProcessOutputTypes.SYSTEM);
      final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
      println(valuesOrderToString(before), ProcessOutputTypes.SYSTEM);

      print("after: ", ProcessOutputTypes.SYSTEM);
      final Map<Integer, TraceElement> after = info.getValuesOrderAfter();
      println(valuesOrderToString(after), ProcessOutputTypes.SYSTEM);
    }
  }

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
    return String.format("\t %s -> %d -> %s", before, elementTime, after);
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

  @NotNull
  protected String getRelativeTestPath() {
    return "debug";
  }
}
