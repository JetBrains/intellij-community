package com.intellij.structuralsearch;

import com.intellij.dupLocator.DefaultDuplocatorState;
import com.intellij.dupLocator.DuplicatesTestCase;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyDuplicatesTest extends DuplicatesTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/structuralsearch/testData/groovy/duplicates/";
  }

  @Override
  protected Language[] getLanguages() {
    return new Language[]{GroovyFileType.GROOVY_LANGUAGE};
  }

  @Override
  protected void findAndCheck(String fileName,
                              boolean distinguishVars,
                              boolean distinguishMethods,
                              boolean distinguishListerals,
                              int patternCount,
                              String suffix,
                              int lowerBound) throws Exception {
    final DefaultDuplocatorState state = (DefaultDuplocatorState)DuplocatorUtil.registerAndGetState(GroovyFileType.GROOVY_LANGUAGE);
    final boolean oldFuncs = state.DISTINGUISH_FUNCTIONS;
    final boolean oldLits = state.DISTINGUISH_LITERALS;
    final boolean oldVars = state.DISTINGUISH_VARIABLES;
    final int oldLowerBound = state.LOWER_BOUND;

    try {
      state.DISTINGUISH_FUNCTIONS = distinguishMethods;
      state.DISTINGUISH_LITERALS = distinguishListerals;
      state.DISTINGUISH_VARIABLES = distinguishVars;
      state.LOWER_BOUND = lowerBound;

      doFindAndCheck(fileName, patternCount, suffix);
    }
    finally {
      state.DISTINGUISH_FUNCTIONS = oldFuncs;
      state.DISTINGUISH_LITERALS = oldLits;
      state.DISTINGUISH_VARIABLES = oldVars;
      state.LOWER_BOUND = oldLowerBound;
    }
  }

  public void test1() throws Exception {
    doTest("grdups1.groovy", false, true, false, 1, 2, "_2", 1);
    doTest("grdups1.groovy", true, true, true, 1, 1, "_0", 1);
    doTest("grdups1.groovy", false, true, true, 1, 1, "_1", 1);
  }

  public void test2() throws Exception {
    doTest("grdups2.groovy", false, false, true, 4, 3, "", 1);
  }

  public void test3() throws Exception {
    doTest("grdups3.groovy", true, true, true, 2, 1, "", 8);
  }

  public void test4() throws Exception {
    // todo: move detection of code blocks to DuplicatesProfile
    doTest("grdups4.groovy", true, true, true, 1, 1, "_0", 8);
    doTest("grdups4.groovy", true, true, false, 1, 1, "_1", 8);
  }

  public void test5() throws Exception {
    doTest("grdups5.groovy", true, true, true, 1, 1, "", 10);
  }
}
