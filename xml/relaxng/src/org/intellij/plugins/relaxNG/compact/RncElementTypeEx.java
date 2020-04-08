/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.intellij.plugins.relaxNG.compact;

import com.intellij.lang.ASTNode;
import com.intellij.util.NotNullFunction;
import org.intellij.plugins.relaxNG.compact.psi.RncElement;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

class RncElementTypeEx<C extends RncElement> extends RncElementType implements NotNullFunction<ASTNode, C> {
  private final Constructor<? extends C> myConstructor;

  RncElementTypeEx(String name, Class<? extends C> clazz) {
    super(name);
    assert !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
    try {
      myConstructor = clazz.getConstructor(ASTNode.class);
    } catch (NoSuchMethodException e) {
      throw new Error(e);
    }
  }

  @Override
  @NotNull
  public final C fun(ASTNode node) {
    try {
      return myConstructor.newInstance(node);
    } catch (Exception e) {
      throw new Error(e);
    }
  }
}
