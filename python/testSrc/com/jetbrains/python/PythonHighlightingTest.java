package com.jetbrains.python;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.awt.*;

/**
 * Test highlighting added by annotators.
 *
 * @author yole
 */
public class PythonHighlightingTest extends PyLightFixtureTestCase {
  private static final String TEST_PATH = "/highlighting/";

  public void testBuiltins() throws Exception {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey;
    TextAttributes xAttributes;
    
    xKey = TextAttributesKey.find("PY.BUILTIN_NAME");
    xAttributes = new TextAttributes(Color.green, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.PREDEFINED_USAGE");
    xAttributes = new TextAttributes(Color.yellow, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testDeclarations() throws Exception {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey = TextAttributesKey.find("PY.CLASS_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(Color.blue, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.FUNC_DEFINITION");
    xAttributes = new TextAttributes(Color.red, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.PREDEFINED_DEFINITION");
    xAttributes = new TextAttributes(Color.green, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testDocStrings() throws Exception {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey = TextAttributesKey.find("PY.DOC_COMMENT");
    TextAttributes xAttributes = new TextAttributes(Color.blue, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testAssignmentTargets() throws Exception {
    doTest(true, false);
  }

  public void testReturnOutsideOfFunction() throws Exception {
    doTest();
  }

  public void testContinueInFinallyBlock() throws Exception {
    doTest(false, false);
  }

  public void testReturnWithArgumentsInGenerator() throws Exception {
    doTest();
  }

  public void testYieldOutsideOfFunction() throws Exception {
    doTest();
  }

  public void testMalformedStringUnterminated() throws Exception {
    doTest();
  }

  public void testMalformedStringEscaped() throws Exception {
    doTest(false, false);
  }

  public void testStringEscapedOK() throws Exception {
    doTest();
  }

  public void testStringMixedSeparatorsOK() throws Exception {   // PY-299
    doTest();
  }

  public void testStringBytesLiteralOK() throws Exception {
    doTest(LanguageLevel.PYTHON26, true, true);
  }

  public void testArgumentList() throws Exception {
    doTest(true, false);
  }

  public void testRegularAfterVarArgs() throws Exception {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testKeywordOnlyArguments() throws Exception {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testMalformedStringTripleQuoteUnterminated() throws Exception {
    doTest();
  }

  public void testUnsupportedFeaturesInPython2() throws Exception {
    doTest(LanguageLevel.PYTHON25, true, false);
  }

  public void testUnsupportedFeaturesInPython3() throws Exception {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testYieldInNestedFunction() throws Exception {
    // highlight func declaration first, lest we get an "Extra fragment highlighted" error.
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey = TextAttributesKey.find("PY.FUNC_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(Color.red, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  private void doTest(final LanguageLevel languageLevel, final boolean checkWarnings, final boolean checkInfos) throws Exception {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(checkWarnings, checkInfos);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  private void doTest() throws Exception {
    final String TEST_PATH = "/highlighting/";
    myFixture.testHighlighting(true, true, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) throws Exception {
    myFixture.testHighlighting(checkWarnings, checkInfos, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }

}
