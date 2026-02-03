// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.generics;

import java.util.List;

public abstract class JGenericMiddleClass<T extends Number> implements JGenericInterface<T> {

  @Override
  public T returningT() {
    return null;
  }

  @Override
  public T[] returningAT() {
    return null;
  }

  @Override
  public Iterable<T> returningLT() {
    return null;
  }

  @Override
  public abstract void acceptingT(T val);

  @Override
  public void acceptingAT(T[] val) {

  }

  @Override
  public void acceptingLT(List<T> val) {

  }
}
