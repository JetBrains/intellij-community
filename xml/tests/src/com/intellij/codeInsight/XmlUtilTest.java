// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.xml.util.XmlUtil;
import junit.framework.TestCase;

public class XmlUtilTest extends TestCase {

  public void testDecodeEntityRef() {
    Disposable myRoot = Disposer.newDisposable();
    DefaultLogger.disableStderrDumping(myRoot);
    assertEquals('&', XmlUtil.getCharFromEntityRef("&amp;"));
    try {
      UsefulTestCase.assertThrows(AssertionError.class, () -> XmlUtil.getCharFromEntityRef("&unknown;"));
    }
    finally {
      Disposer.dispose(myRoot);
    }
  }
}
