// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.xml.util.XmlUtil;
import junit.framework.TestCase;

public class XmlUtilTest extends TestCase {

  public void testDecodeEntityRef() throws Exception {
    assertEquals('&', XmlUtil.getCharFromEntityRef("&amp;"));
    Disposable myRoot = Disposer.newDisposable();
    DefaultLogger.disableStderrDumping(myRoot);
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      try {
        XmlUtil.getCharFromEntityRef("&unknown;");
      }
      catch (AssertionError ignore) {
        return;
      }
      finally {
        Disposer.dispose(myRoot);
      }
      fail("Exception should be thrown");
    });
  }
}
