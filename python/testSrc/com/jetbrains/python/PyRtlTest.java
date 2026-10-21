// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.editor.impl.AbstractRtlTest;
import com.intellij.testFramework.junit5.TestApplication;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import org.junit.jupiter.api.Test;

@Subsystems.IDE
@Layers.Functional
@TestApplication
public class PyRtlTest extends AbstractRtlTest {
  @Test
  public void testCommentWithoutSpace() {
    myFixture.runTest(() -> {
      checkBidiRunBoundaries("#|R", "py");
    });
  }
}
