// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.generics2;

public abstract class JGenericMiddleClassWithAbstractOverride<T extends Number> implements JGenericInterface<T> {

  @Override
  public abstract T genericMethod(T val);
}
