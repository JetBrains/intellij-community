// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.inheritFromPackageLocal;

import org.jetbrains.annotations.ApiStatus.Internal;

//@formatter:off
@SuppressWarnings("unused")
public class JPublicInheritor extends JPackageLocalClass {

  public              JPublicInheritor() { }
  protected           JPublicInheritor(byte b)    { super(b); }
  @Internal public    JPublicInheritor(int b)     { super(b); }
  @Internal protected JPublicInheritor(long b)    { super(b); }
                      JPublicInheritor(double c)  { super(c); }

  @Override
  public JPublicInheritor packagePrivateMethodWithPackagePrivateReturnType() {
    return null;
  }
}
//@formatter:on
