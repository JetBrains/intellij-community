// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.generics;

import java.util.List;

@SuppressWarnings("unused")
public class JGenericChildClass extends JGenericMiddleClass<Double> {

  @Override
  public Double returningT() {
    return super.returningT();
  }

  @Override
  public Double[] returningAT() {
    return super.returningAT();
  }

  @Override
  public Iterable<Double> returningLT() {
    return super.returningLT();
  }

  @Override
  public void acceptingT(Double val) {
  }

  @Override
  public void acceptingAT(Double[] val) {
    super.acceptingAT(val);
  }

  @Override
  public void acceptingLT(List<Double> val) {
    super.acceptingLT(val);
  }
}
