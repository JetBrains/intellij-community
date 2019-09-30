package com.jetbrains.python.completion.ml;

import com.jetbrains.python.codeInsight.completion.ml.PythonMLRankingProvider;
import org.junit.Test;

public class PythonModelMetadataTest {
  @Test
  public void testMetadataConsistent() {
    new PythonMLRankingProvider().assertModelMetadataConsistent();
  }
}
