// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.inheritFromPackageLocal;

import org.jetbrains.annotations.ApiStatus.Internal;

//@formatter:off
@SuppressWarnings({"unused", "ProtectedMemberInFinalClass"})
public final class JPublicFinalInheritor extends JPackageLocalClass {

  public              JPublicFinalInheritor() { }
  protected           JPublicFinalInheritor(byte b)    { super(b); }
  @Internal public    JPublicFinalInheritor(int b)     { super(b); }
  @Internal protected JPublicFinalInheritor(long b)    { super(b); }
                      JPublicFinalInheritor(double c)  { super(c); }

  @Override
  public JPublicFinalInheritor packagePrivateMethodWithPackagePrivateReturnType() {
    return null;
  }
}
//@formatter:on
