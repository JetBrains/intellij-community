package com.intellij.structuralsearch;

import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;

/**
 * @author Maxim.Mossienko
 */
public class OptimizedSearchScanTest extends StructuralSearchTestCase {
  public void testClassByQName() throws Exception {
    //String plan = findWordsToBeUsedWhenSearchingFor("A.f");
    //assertEquals("[in code:f]", plan);
  }

  private String findWordsToBeUsedWhenSearchingFor(final String s) {
    findMatchesCount("{}",s);
    return PatternCompiler.getLastFindPlan();
  }
}
