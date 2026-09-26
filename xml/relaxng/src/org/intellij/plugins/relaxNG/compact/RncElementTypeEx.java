// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public final @NotNull C fun(ASTNode node) {
    try {
      return myConstructor.newInstance(node);
    } catch (Exception e) {
      throw new Error(e);
    }
  }
}
