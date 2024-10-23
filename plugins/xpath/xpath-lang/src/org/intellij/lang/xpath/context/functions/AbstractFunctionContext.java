// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.context.functions;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.context.ContextType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractFunctionContext implements FunctionContext {
  private static final Map<ContextType, FunctionContext> ourInstances = new ConcurrentHashMap<>();

  @Unmodifiable
  private final Map<Pair<QName, Integer>, Function> myFunctions;
  private final Map<QName, Function> myDefaultMap = new HashMap<>();

  protected AbstractFunctionContext(ContextType contextType) {
    assert !ourInstances.containsKey(contextType);

    //noinspection AbstractMethodCallInConstructor,RedundantUnmodifiable
    myFunctions = Collections.unmodifiableMap(ContainerUtil.union(createFunctionMap(contextType), getProvidedFunctions(contextType)));

    for (Map.Entry<Pair<QName, Integer>, Function> entry : myFunctions.entrySet()) {
      final Function function = entry.getValue();

      final Function prev = myDefaultMap.get(entry.getKey().first);
      if (prev != null) {
        if (prev.getParameters().length > function.getParameters().length) {
          myDefaultMap.put(entry.getKey().first, function);
        }
      } else {
        myDefaultMap.put(entry.getKey().first, function);
      }
    }
  }

  @Unmodifiable
  protected abstract Map<Pair<QName, Integer>, Function> createFunctionMap(ContextType contextType);

  private static Map<Pair<QName, Integer>, Function> getProvidedFunctions(ContextType contextType) {
    final Map<Pair<QName, Integer>, Function> map = new HashMap<>();
    final List<Pair<QName, ? extends Function>> availableFunctions = XPathFunctionProvider.getAvailableFunctions(contextType);
    for (Pair<QName, ? extends Function> pair : availableFunctions) {
      map.put(Pair.create(pair.first, pair.second.getParameters().length), pair.second);
    }
    return Collections.unmodifiableMap(map);
  }

  protected static FunctionContext getInstance(ContextType contextType, Factory<? extends FunctionContext> factory) {
    return ourInstances.computeIfAbsent(contextType, k -> factory.create());
  }

  @Override
  public Map<Pair<QName, Integer>, Function> getFunctions() {
    return myFunctions;
  }

  @Override
  public @Nullable Function resolve(QName name, int argCount) {
    if (!myDefaultMap.containsKey(name)) return null;

    final Function function = getFunctions().get(Pair.create(name, argCount));
    if (function != null) {
      return function;
    }
    return myDefaultMap.get(name);
  }
}