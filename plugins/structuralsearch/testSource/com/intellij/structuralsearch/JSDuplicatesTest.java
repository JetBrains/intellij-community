package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.JavascriptLanguage;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene.Kudelevsky
 */
public class JSDuplicatesTest extends DuplicatesTestCase {
  @NonNls
  private static final String BASE_PATH = "/js/duplicates/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test1() throws Exception {
    doTest("jsdup1.js", true, true, true, 1, 1, "_0", 10);
    doTest("jsdup1.js", true, false, true, 1, 1, "_1", 10);
    doTest("jsdup1.js", false, false, true, 1, 1, "_2", 10);
    doTest("jsdup1.js", false, false, false, 1, 1, "_3", 10);
  }

  public void test2() throws Exception {
    doTest("jsdup2.js", false, true, false, 1, 0, "", 10);
  }

  public void testAs1() throws Exception {
    doTest("asdups1.as", false, false, true, 1, 1, "", 2);
  }

  public void testAs2() throws Exception {
    doTest("asdups2.as", false, false, true, 3, 0, "", 4);
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected Language[] getLanguages() {
    return new Language[]{JavascriptLanguage.INSTANCE, JavaScriptSupportLoader.ECMA_SCRIPT_L4};
  }
}

