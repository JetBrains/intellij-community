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

  public void testBuiltins() {
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

  public void testDeclarations() {
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

  public void testDocStrings() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey = TextAttributesKey.find("PY.DOC_COMMENT");
    TextAttributes xAttributes = new TextAttributes(Color.blue, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testAssignmentTargets() {
    setLanguageLevel(LanguageLevel.PYTHON26);
    doTest(true, false);
  }

  public void testAssignmentTargets3K() {
    doTest(LanguageLevel.PYTHON30, true, false);    
  }

  public void testReturnOutsideOfFunction() {
    doTest();
  }

  public void testContinueInFinallyBlock() {
    doTest(false, false);
  }

  public void testReturnWithArgumentsInGenerator() {
    doTest();
  }

  public void testYieldOutsideOfFunction() {
    doTest();
  }

  public void testMalformedStringUnterminated() {
    doTest();
  }

  public void testMalformedStringEscaped() {
    doTest(false, false);
  }

  /*
  public void testStringEscapedOK() {
    doTest();
  }
  */

  public void testStringMixedSeparatorsOK() {   // PY-299
    doTest();
  }

  public void testStringBytesLiteralOK() {
    doTest(LanguageLevel.PYTHON26, true, true);
  }

  public void testArgumentList() {
    doTest(true, false);
  }

  public void testRegularAfterVarArgs() {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testKeywordOnlyArguments() {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testMalformedStringTripleQuoteUnterminated() {
    doTest();
  }

  public void testMixedTripleQuotes() {   // PY-2806
    doTest();
  }

  public void testOddNumberOfQuotes() {  // PY-2802
    doTest(true, false);
  }

  public void testEscapedBackslash() {  // PY-2994
    doTest(true, false);
  }

  public void testUnsupportedFeaturesInPython3() {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testYieldInNestedFunction() {
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

  public void testUnicodeOrByte25() {
    doTest(LanguageLevel.PYTHON25, true, true);
  }

  public void testUnicodeOrByte26future() {
    doTest(LanguageLevel.PYTHON26, true, true);
  }

  public void testUnicodeOrByte30() {
    doTest(LanguageLevel.PYTHON30, true, true);
  }


  // ---
  private void doTest(final LanguageLevel languageLevel, final boolean checkWarnings, final boolean checkInfos) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(checkWarnings, checkInfos);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  private void doTest() {
    final String TEST_PATH = "/highlighting/";
    myFixture.testHighlighting(true, true, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    myFixture.testHighlighting(checkWarnings, checkInfos, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }

}
