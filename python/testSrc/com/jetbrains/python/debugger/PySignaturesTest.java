// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.allure.Layers;

import junit.framework.TestCase;

import static com.jetbrains.python.debugger.PySignatureCacheManagerImpl.changeSignatureString;

@Subsystems.Debugger
@Layers.Functional
public class PySignaturesTest extends TestCase{
  public void testUnionFlatten() {
    String path = "/script.py";
    PySignature foo = new PySignature(path, "foo");
    assertEquals("foo\tx:Union[unicode, str]", changeSignatureString(path, foo.addArgument("x", "unicode"), changeSignatureString(path, foo.addArgument("x", "str"), "foo")));
  }
}
