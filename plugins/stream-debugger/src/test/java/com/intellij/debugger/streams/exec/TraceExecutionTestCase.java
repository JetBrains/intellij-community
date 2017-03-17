package com.intellij.debugger.streams.exec;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.OutputChecker;
import com.intellij.debugger.streams.JdkManager;
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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.Value;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceExecutionTestCase extends DebuggerTestCase {
  private final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();
  private final TraceExpressionBuilder myExpressionBuilder = new TraceExpressionBuilderImpl();
  private final TraceResultInterpreter myResultInterpreter = new TraceResultInterpreterImpl();

  public void testSimple() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testFilter() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  @Override
  protected void setUp() throws Exception {
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), JdkManager.JDK18_PATH);
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

  @Override
  protected Sdk getTestProjectJdk() {
    return JdkManager.getMockJdk18();
  }

  protected List<TraceInfo> doTest(boolean isResultNull) throws InterruptedException, ExecutionException, InvocationTargetException {
    final String name = getTestName(false);

    createLocalProcess(name);
    doWhenXSessionPausedThenResume(() -> {
      printContext(getDebugProcess().getDebuggerContext());
      final XDebugSession session = getDebuggerSession().getXDebugSession();
      assertNotNull(session);

      final StreamChain chain = ApplicationManager.getApplication().runReadAction((Computable<StreamChain>)() -> {
        final PsiElement elementAtBreakpoint = myPositionResolver.getNearestElementToBreakpoint(session);
        assertNotNull(elementAtBreakpoint);
        return StreamChainBuilder.tryBuildChain(elementAtBreakpoint);
      });

      assertNotNull(chain);
      println(chain.getText(), ProcessOutputTypes.SYSTEM);
      final StreamTracer tracer = new EvaluateExpressionTracer(session, myExpressionBuilder, myResultInterpreter);
      tracer.trace(chain, new TracingCallback() {
        @Override
        public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
          final Value resultValue = result.getResult();
          handleResultValue(resultValue, isResultNull);

          final List<TraceInfo> trace = result.getTrace();
          handleTrace(trace);

          final ResolvedTracingResult resolvedTrace = result.resolve();
          handleResultValue(resolvedTrace.getResult(), isResultNull);
          handleResolvedTrace(resolvedTrace);
        }

        @Override
        public void failed(@NotNull String traceExpression, @NotNull String reason) {
          fail("evaluation failed");
        }
      });
    });
    return Collections.emptyList();
  }

  protected void handleResultValue(@Nullable Value result, boolean mustBeNull) {
    if (mustBeNull) {
      assertNull(result);
    }
    else {
      assertNotNull(result);
      println(result.type().name(), ProcessOutputTypes.SYSTEM);
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

  private String evalTimesRepresentation(@NotNull String before, int elementTime, @NotNull String after) {
    before = before.isEmpty() ? "nothing" : before;
    after = after.isEmpty() ? "nothing" : after;
    return String.format("\t %s -> %d -> %s", before, elementTime, after);
  }

  private void checkTracesIsCorrectInBothDirections(@NotNull List<ResolvedTrace> resolvedTraces) {
    for (int i = 1, size = resolvedTraces.size(); i < size; i++) {
      final ResolvedTrace previous = resolvedTraces.get(i - 1);
      final ResolvedTrace current = resolvedTraces.get(i);
      checkNeighborTraces(previous, current);
    }
  }

  private void checkNeighborTraces(@NotNull ResolvedTrace left, @NotNull ResolvedTrace right) {
    final Set<TraceElement> leftValues = new HashSet<>(left.getValues());
    final Set<TraceElement> rightValues = new HashSet<>(right.getValues());

    checkThatMappingsIsCorrect(leftValues, rightValues, left::getNextValues, right::getPreviousValues);
    checkThatMappingsIsCorrect(rightValues, leftValues, right::getPreviousValues, left::getNextValues);
  }

  private void checkThatMappingsIsCorrect(@NotNull Set<TraceElement> prev,
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
  private String valuesOrderToString(@NotNull Map<Integer, TraceElement> values) {
    return StreamEx.of(values.keySet()).sorted().joining(",");
  }

  protected String getRelativeTestPath() {
    return "debug4";
  }
}
