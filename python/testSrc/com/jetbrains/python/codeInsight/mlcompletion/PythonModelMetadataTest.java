// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion;

import org.junit.Test;

public class PythonModelMetadataTest {
  @Test
  public void testMetadataConsistent() {
    new PythonMLRankingProvider().assertModelMetadataConsistent();
  }
}
