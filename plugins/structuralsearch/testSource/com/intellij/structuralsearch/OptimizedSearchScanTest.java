package com.intellij.structuralsearch;

import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.idea.Bombed;

import java.util.Calendar;

/**
 * @author Maxim.Mossienko
 */
@Bombed(day = 31, description = "support it", month = Calendar.JANUARY, user = "maxim.mossienko", year = 2008)
public class OptimizedSearchScanTest extends StructuralSearchTestCase {
  public void testClassByQName() throws Exception {
    String plan = findWordsToBeUsedWhenSearchingFor("A.f");
    assertEquals("[in code:f]", plan);
  }

  private String findWordsToBeUsedWhenSearchingFor(final String s) {
    findMatchesCount("{}",s);
    return PatternCompiler.getLastFindPlan();
  }
}
