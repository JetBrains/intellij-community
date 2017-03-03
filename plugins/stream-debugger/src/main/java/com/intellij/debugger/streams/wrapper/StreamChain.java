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
package com.intellij.debugger.streams.wrapper;

import com.intellij.debugger.streams.resolve.ResolvedCall;
import com.intellij.debugger.streams.resolve.ResolvedCallImpl;
import com.intellij.debugger.streams.resolve.ResolverFactoryImpl;
import com.intellij.debugger.streams.resolve.ValuesOrderResolver;
import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.streams.trace.smart.TraceElementImpl;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.sun.jdi.Value;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamChain {
  private static final Set<String> SUPPORTED_PRODUCERS = StreamEx.of("stream", "iterate", "generate", "range", "rangeClosed").toSet();
  private static final Set<String> SUPPORTED_INTERMEDIATE =
    StreamEx.of("limit", "flatMap", "distinct", "map", "filter", "mapToInt", "mapToLong", "MapToDouble", "sorted", "boxed", "peek").toSet();
  private static final Set<String> SUPPORTED_TERMINATION = StreamEx.of("collect", "sum", "reduce").toSet();

  private static final ThreadLocal<PsiMethodCallExpression[]> SEARCH_RESULT = new ThreadLocal<>();
  private static final PsiElementVisitor STREAM_CALL_FINDER = new JavaRecursiveElementWalkingVisitor() {
    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      // ignore lambda calls
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method != null) {
        final PsiType type = method.getReturnType();
        if (type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
          SEARCH_RESULT.get()[0] = expression;
        }
      }
    }
  };

  private final List<MethodCall> myCalls;

  private enum CallType {
    PRODUCER, INTERMEDIATE, TERMINATOR, UNKNOWN
  }

  static {
    SEARCH_RESULT.set(new PsiMethodCallExpression[1]);
  }

  public StreamChain(@NotNull List<MethodCall> calls) {
    myCalls = calls;
  }

  @Nullable
  public static StreamChain tryBuildChain(@NotNull PsiElement elementAtCursor) {
    final PsiMethodCallExpression call = tryFindStreamCall(elementAtCursor);
    if (call != null) {
      final List<MethodCall> streamCalls = new ArrayList<>();
      streamCalls.add(new ProducerStreamCall(call));
      PsiElement current = call.getParent();
      while (current != null) {
        if (current instanceof PsiMethodCallExpression) {
          final PsiMethod method = ((PsiMethodCallExpression)current).resolveMethod();
          final String methodName = method != null ? method.getName() : null;
          if (methodName != null && isSupportedStreamOp(method.getName())) {
            streamCalls.add(new StreamCallImpl((PsiMethodCallExpression)current));
          }
        }

        current = current.getParent();
      }

      // at least of producer and terminator
      if (streamCalls.size() < 2) {
        return null;
      }

      return new StreamChain(streamCalls);
    }

    return null;
  }

  public static boolean checkStreamExists(@NotNull PsiElement elementAtCursor) {
    return tryFindStreamCall(elementAtCursor) != null;
  }

  public List<MethodCall> getCalls() {
    return Collections.unmodifiableList(myCalls);
  }

  public int length() {
    return myCalls.size();
  }

  public String getCallName(int ix) {
    return myCalls.get(ix).getName();
  }

  @NotNull
  public String getText() {
    final Iterator<MethodCall> iterator = myCalls.iterator();
    final StringBuilder builder = new StringBuilder();

    while (iterator.hasNext()) {
      final MethodCall call = iterator.next();
      builder.append(call.getName()).append(call.getArguments());
      if (iterator.hasNext()) {
        builder.append(EvaluateExpressionTracerBase.LINE_SEPARATOR).append(".");
      }
    }

    return builder.toString();
  }

  @NotNull
  public List<ResolvedCall> resolveCalls(@NotNull TracingResult result) {
    if (myCalls.isEmpty()) {
      return Collections.emptyList();
    }

    final Value res = result.getResult();
    final List<TraceInfo> trace = result.getTrace();

    assert myCalls.size() == trace.size() + 1;
    final List<ResolvedCall> resolvedCalls = new ArrayList<>();

    Map<TraceElement, List<TraceElement>> prevResolved = Collections.emptyMap();
    for (int i = 1; i < myCalls.size() - 1; i++) {
      final Map<Integer, TraceElement> prev = trace.get(i - 1).getValuesOrder();
      final Map<Integer, TraceElement> next = trace.get(i).getValuesOrder();
      final MethodCall previousCall = myCalls.get(i - 1);
      final MethodCall currentCall = myCalls.get(i);

      final ValuesOrderResolver resolver = ResolverFactoryImpl.getInstance().getResolver(currentCall.getName());
      final Pair<Map<TraceElement, List<TraceElement>>, Map<TraceElement, List<TraceElement>>> resolved = resolver.resolve(prev, next);

      resolvedCalls.add(new ResolvedCallImpl(previousCall, prevResolved, resolved.getFirst()));
      prevResolved = resolved.getSecond();
    }

    resolvedCalls
      .add(new ResolvedCallImpl(myCalls.get(myCalls.size() - 2), prevResolved, prevResolved));

    if (res != null) {
      final TraceElement resultElement = new TraceElementImpl(Integer.MAX_VALUE, res);
      resolvedCalls
        .add(new ResolvedCallImpl(myCalls.get(myCalls.size() - 1), Collections.emptyMap(),
                                  Collections.singletonMap(resultElement, Collections.singletonList(resultElement))));
    }

    return resolvedCalls;
  }

  @Nullable
  private static PsiMethodCallExpression tryFindStreamCall(@NotNull PsiElement startElement) {
    PsiElement current = startElement;

    // find nearest node with children.
    while (current != null && current.getChildren().length == 0) {
      current = current.getNextSibling();
    }

    final PsiElement candidate = current;
    SEARCH_RESULT.get()[0] = null;
    if (candidate != null) {
      // find the deepest call with stream as result
      candidate.accept(STREAM_CALL_FINDER);
    }

    return SEARCH_RESULT.get()[0];
  }

  private static boolean isSupportedStreamOp(@NotNull String name) {
    return !getType(name).equals(CallType.UNKNOWN);
  }

  private static CallType getType(@NotNull String name) {
    if (SUPPORTED_INTERMEDIATE.contains(name)) {
      return CallType.INTERMEDIATE;
    }
    if (SUPPORTED_PRODUCERS.contains(name)) {
      return CallType.PRODUCER;
    }

    return SUPPORTED_TERMINATION.contains(name)
           ? CallType.TERMINATOR
           : CallType.UNKNOWN;
  }
}
