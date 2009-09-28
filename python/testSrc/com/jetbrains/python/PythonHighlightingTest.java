package com.jetbrains.python;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * Test highlighting added by annotators.
 * @author yole
 */
public class PythonHighlightingTest extends DaemonAnalyzerTestCase {
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/highlighting/";
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
    doTest();
  }

  public void testReturnOutsideOfFunction() throws Exception {
    doTest();
  }

  public void testContinueInFinallyBlock() throws Exception {
    doTest();
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

  private void doTest() throws Exception {
    doTest(getTestName(true) + PyNames.DOT_PY, true, true);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(getTestName(true) + PyNames.DOT_PY, checkWarnings, checkInfos);
  }

}
