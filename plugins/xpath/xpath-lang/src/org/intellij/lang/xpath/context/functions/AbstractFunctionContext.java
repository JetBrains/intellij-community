/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.xpath.context.functions;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.context.ContextType;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.01.11
*/
public abstract class AbstractFunctionContext implements FunctionContext {
  private static final Map<ContextType, FunctionContext> ourInstances = new HashMap<>();

  private final Map<Pair<QName, Integer>, Function> myFunctions;
  private final Map<QName, Function> myDefaultMap = new HashMap<>();

  protected AbstractFunctionContext(ContextType contextType) {
    assert !ourInstances.containsKey(contextType);

    //noinspection AbstractMethodCallInConstructor
    myFunctions = Collections.unmodifiableMap(new HashMap<>(
      ContainerUtil.union(createFunctionMap(contextType), getProvidedFunctions(contextType))));

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
    ourInstances.put(contextType, this);
  }

  protected abstract Map<Pair<QName, Integer>, Function> createFunctionMap(ContextType contextType);

  private static Map<Pair<QName, Integer>, Function> getProvidedFunctions(ContextType contextType) {
    final Map<Pair<QName, Integer>, Function> map = new HashMap<>();
    final List<Pair<QName, ? extends Function>> availableFunctions = XPathFunctionProvider.getAvailableFunctions(contextType);
    for (Pair<QName, ? extends Function> pair : availableFunctions) {
      map.put(Pair.create(pair.first, pair.second.getParameters().length), pair.second);
    }
    return Collections.unmodifiableMap(map);
  }

  protected static synchronized FunctionContext getInstance(ContextType contextType, Factory<FunctionContext> factory) {
    FunctionContext context = ourInstances.get(contextType);
    if (context == null) {
      context = factory.create();
      ourInstances.put(contextType, context);
    }
    return context;
  }

  public Map<Pair<QName, Integer>, Function> getFunctions() {
    return myFunctions;
  }

  @Nullable
  @Override
  public Function resolve(QName name, int argCount) {
    if (!myDefaultMap.containsKey(name)) return null;

    final Function function = getFunctions().get(Pair.create(name, argCount));
    if (function != null) {
      return function;
    }
    return myDefaultMap.get(name);
  }
}