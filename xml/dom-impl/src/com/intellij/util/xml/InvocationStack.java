/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class InvocationStack<T> {
  public static final InvocationStack<Object> INSTANCE = new InvocationStack<Object>();

  private final ThreadLocal<List<Pair<JavaMethodSignature,T>>> myCallStacks = new ThreadLocal<List<Pair<JavaMethodSignature, T>>>();

  public final void push(Method method, T o) {
    JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    List<Pair<JavaMethodSignature, T>> stack = myCallStacks.get();
    if (stack == null) {
      myCallStacks.set(stack = new ArrayList<Pair<JavaMethodSignature, T>>());
    }
    stack.add(Pair.create(signature, o));
  }
                                                 
  @Nullable
  public final T findDeepestInvocation(Method method, Condition<T> stopAt) {
    final List<Pair<JavaMethodSignature, T>> stack = myCallStacks.get();

    JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    for (int i = stack.size() - 2; i >= 0; i--) {
      final Pair<JavaMethodSignature, T> pair = stack.get(i);
      if (stopAt.value(pair.second)) {
        return stack.get(i + 1).second;
      }
      if (pair.first != signature) {
        return null;
      }
    }
    return stack.isEmpty() ? null : stack.get(0).second;
  }

  public final Pair<JavaMethodSignature,T> pop() {
    final List<Pair<JavaMethodSignature, T>> list = myCallStacks.get();
    return list.remove(list.size() - 1);
  }
}
