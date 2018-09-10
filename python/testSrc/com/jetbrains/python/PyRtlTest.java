// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.editor.impl.AbstractRtlTest;

public class PyRtlTest extends AbstractRtlTest {
  public void testCommentWithoutSpace() {
    checkBidiRunBoundaries("#|R", "py");
  }
}
