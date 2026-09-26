// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.generics2;

@SuppressWarnings("unused")
public final class JChildClasses {

  private JChildClasses() { }

  public static class JChildClassFromMiddleWithoutOverride extends JGenericMiddleClass<Double> {

    @Override
    public Double genericMethod(Double val) {
      return null;
    }
  }

  public static class JChildClassFromMiddleWithAbstractOverride extends JGenericMiddleClassWithAbstractOverride<Double> {

    @Override
    public Double genericMethod(Double val) {
      return null;
    }
  }

  public static class JChildClassFromMiddleWithOverride extends JGenericMiddleClassWithOverride<Double> {
  }

  public static class JChildClassWithOverrideFromMiddleWithOverride extends JGenericMiddleClassWithOverride<Double> {

    @Override
    public Double genericMethod(Double val) {
      return super.genericMethod(val);
    }
  }
}
