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
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.streams.remote.InvokeMethodProxy;
import com.intellij.debugger.streams.remote.ProxyBase;
import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.streams.trace.smart.TraceElementImpl;
import com.intellij.debugger.streams.trace.smart.handler.PeekCall;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.trace.smart.resolve.impl.ValuesOrderInfo;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class MapStreamTracerImpl extends EvaluateExpressionTracerBase {
  private static final Logger LOG = Logger.getInstance(MapStreamTracerImpl.class);

  private static final String DEFS =
    "final java.util.Map<Integer, Object> map = new java.util.HashMap<>();\n" +
    "final java.util.concurrent.atomic.AtomicInteger time = new java.util.concurrent.atomic.AtomicInteger(0);\n" +
    "final java.util.function.Function<Integer, Object> mapSupplier = x -> new java.util.LinkedHashMap<>();\n";
  private static final String RETURN_ACTION = "map";
  private static final String PEEK_ACTION_FORMAT =
    "x -> ((Map<Integer, Object>)map.computeIfAbsent(%d, mapSupplier)).put(time.getAndIncrement(), x)";

  public MapStreamTracerImpl(@NotNull XDebugSession session) {
    super(session);
  }

  @NotNull
  @Override
  protected String getTraceExpression(@NotNull StreamChain chain) {
    final String call = "Object res = " + insertPeeks(chain).getText() + ";\n";
    final String saveResult = "map.put(-1, java.util.Collections.singletonMap(-1, res)); \n";
    String result = DEFS + call + saveResult + RETURN_ACTION;
    LOG.info(result);
    return result;
  }

  @NotNull
  @Override
  protected TracingResult interpretResult(@NotNull StreamChain chain, @NotNull InvokeMethodProxy result) {
    final Map<Integer, Map<Integer, Value>> map;
    try {
      map = convertToLocalMap(result);
      final ArrayList<TraceInfo> trace = new ArrayList<>(map.size());
      for (int callNumber = 0, callCount = chain.length(); callNumber < callCount; callNumber++) {
        final Map<Integer, Value> callTrace = map.get(callNumber);
        final Map<Integer, TraceElement> traceResult = new HashMap<>();
        for (final int time : callTrace.keySet()) {
          final Value value = callTrace.get(time);
          traceResult.put(time, new TraceElementImpl(time, value));
        }

        trace.add(new ValuesOrderInfo(chain.getCall(callNumber), Collections.emptyMap(), traceResult));
      }
      final Value res = map.get(-1).get(-1);
      return new MyResult(res, trace);
    }
    catch (EvaluateException e) {
      throw new RuntimeException("Cannot convert trace", e);
    }
  }

  @NotNull
  private static StreamChain insertPeeks(@NotNull StreamChain oldChain) {
    final List<StreamCall> calls = oldChain.getIntermediateCalls();
    final List<StreamCall> result = new ArrayList<>();
    result.add(new PeekCall(String.format(PEEK_ACTION_FORMAT, 0)));
    for (int i = 1; i < calls.size(); i++) {
      result.add(calls.get(i));
      result.add(new PeekCall(String.format(PEEK_ACTION_FORMAT, i)));
    }

    return new StreamChainImpl(oldChain.getProducerCall(), result, oldChain.getTerminationCall());
  }

  @NotNull
  private static Map<Integer, Map<Integer, Value>> convertToLocalMap(@NotNull InvokeMethodProxy invoker) throws EvaluateException {
    final Map<Integer, Map<Integer, Value>> result = new HashMap<>();

    final IteratorProxy iterator = new MapProxy(invoker).iterator();

    while (iterator.hasNext()) {
      final MapEntryProxy entry = iterator.next();
      final int key = entry.key();
      final Map<Integer, Value> value = convertToLocalNestedMap(entry.value());

      result.put(key, value);
    }

    return result;
  }

  @NotNull
  private static Map<Integer, Value> convertToLocalNestedMap(@NotNull InvokeMethodProxy remoteMap) throws EvaluateException {
    final Map<Integer, Value> result = new LinkedHashMap<>();
    final IteratorProxy iterator = new MapProxy(remoteMap).iterator();
    while (iterator.hasNext()) {
      final MapEntryProxy entry = iterator.next();
      final int key = entry.key();
      final Value value = entry.value().getValue();

      result.put(key, value);
    }

    return result;
  }

  private static class MapProxy extends ProxyBase {
    MapProxy(@NotNull InvokeMethodProxy invoker) {
      super(invoker);
    }

    IteratorProxy iterator() throws EvaluateException {
      return new IteratorProxy(call("entrySet").call("iterator"));
    }
  }

  private static class IteratorProxy extends ProxyBase {
    IteratorProxy(@NotNull InvokeMethodProxy invoker) {
      super(invoker);
    }

    boolean hasNext() throws EvaluateException {
      final Value result = evaluate("hasNext", Collections.emptyList());
      return result instanceof BooleanValue && ((BooleanValue)result).value();
    }

    MapEntryProxy next() throws EvaluateException {
      return new MapEntryProxy(call("next"));
    }
  }

  private static class MapEntryProxy extends ProxyBase {
    MapEntryProxy(@NotNull InvokeMethodProxy invoker) {
      super(invoker);
    }

    int key() throws EvaluateException {
      Value result = call("getKey", Collections.emptyList()).evaluate("intValue", Collections.emptyList());
      if (result instanceof IntegerValue) {
        return ((IntegerValue)result).value();
      }

      throw new RuntimeException("Type of expected value is not int");
    }

    @NotNull
    InvokeMethodProxy value() throws EvaluateException {
      return call("getValue");
    }
  }

  private static class MyResult implements TracingResult {
    private final Value myValue;
    private final List<TraceInfo> myCalls;

    MyResult(@Nullable Value streamResult, @NotNull List<TraceInfo> traces) {
      myValue = streamResult;
      myCalls = new ArrayList<>(traces);
    }

    @Nullable
    @Override
    public Value getResult() {
      return myValue;
    }

    @NotNull
    @Override
    public List<TraceInfo> getTrace() {
      return Collections.unmodifiableList(myCalls);
    }
  }
}
