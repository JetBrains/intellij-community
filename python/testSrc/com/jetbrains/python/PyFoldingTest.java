// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.EditorTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.removeFoldingMarkers;
import static com.jetbrains.python.PyElementTypes.ANNOTATION;
import static com.jetbrains.python.PyElementTypes.STRING_LITERAL_EXPRESSION;
import static com.jetbrains.python.PyTokenTypes.END_OF_LINE_COMMENT;
import static com.jetbrains.python.PythonFoldingBuilderKt.FOLDABLE_COLLECTIONS_LITERALS;
import static org.junit.jupiter.params.provider.Arguments.arguments;


public class PyFoldingTest extends PyTestCase {

  @Override
  protected @NotNull String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/folding/";
  }

  protected @NotNull String getTestDataPathFile(@Nullable String fileName) {
    String fileNameToUse = fileName == null ? getTestName(true) : fileName;
    return getTestDataPath() + fileNameToUse + "." + getTestFileExtension();
  }

  protected @NotNull String getTestFileExtension() {
    return PythonFileType.INSTANCE.getDefaultExtension();
  }

  protected void doTest() {
    myFixture.testFolding(getTestDataPathFile(null));
  }

  public void testClassTrailingSpace() {  // PY-2544
    doTest();
  }

  public void testDocString() {
    doTest();
  }

  public void testCustomFolding() {
    doTest();
  }

  public void testImportBlock() {
    doTest();
  }

  public void testBlocksFolding() {
    doTest();
  }

  public void testLongStringsFolding() {
    doTest();
  }

  public void testCollectionsFolding() {
    doTest();
  }

  public void testMultilineComments() {
    doTest();
  }

  public void testNestedFolding() {
    doTest();
  }

  //PY-18928
  public void testCustomFoldingWithComments() {
    doTest();
  }

  // PY-17017
  public void testCustomFoldingAtBlockEnd() {
    doTest();
  }

  // PY-31154
  public void testEmptyStatementListHasNoFolding() {
    doTest();
  }

  public void testCollapseExpandDocCommentsTokenType() {
    myFixture.configureByFile(getTestDataPathFile(null));
    EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());
    checkCollapseExpandRegionsAndDocComments(true);
    checkCollapseExpandRegionsAndDocComments(false);
  }

  private void checkCollapseExpandRegionsAndDocComments(boolean doExpand) {
    final String initial = doExpand ? "CollapseAllRegions" : "ExpandAllRegions";
    final String action = doExpand ? "ExpandDocComments" : "CollapseDocComments";
    final String logAction = doExpand ? "collapsed: " : "expanded: ";

    myFixture.performEditorAction(initial);
    myFixture.performEditorAction(action);

    final Editor editor = myFixture.getEditor();
    for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
      PsiElement element = EditorFoldingInfo.get(editor).getPsiElement(region);
      if (element instanceof PyStringLiteralExpression && ((PyStringLiteralExpression)element).isDocString()) {
        assertEquals(logAction + element.getText(), doExpand, region.isExpanded());
      }
      else {
        assertEquals("not " + logAction + element.getText(), doExpand, !region.isExpanded());
      }
    }
  }

  @SuppressWarnings("unused") // used by JUnit via reflection, see @FieldSource
  static List<?> TWO_STEP_EXPAND_ONE_STEP_COLLAPSE = Arrays.asList(
    arguments(true, true, false),
    arguments(true, false, false),
    arguments(false, true, true),
    arguments(false, false, false)
  );

  @ParameterizedTest(name = "collapseLongCollectionsSetting={0}, doExpand={1}, expectExpandedAfterStep1={2}")
  @FieldSource("TWO_STEP_EXPAND_ONE_STEP_COLLAPSE")
  public void testLongCollectionsTwoStepFolding(boolean collapseLongCollectionsSetting, boolean doExpand, boolean expectExpandedAfterStep1)
    throws Throwable {
    runBare(() -> {
      PythonFoldingSettings.getInstance().COLLAPSE_LONG_COLLECTIONS = collapseLongCollectionsSetting;
      checkCollapseExpandAllAction(FOLDABLE_COLLECTIONS_LITERALS, "collectionsFolding", doExpand, expectExpandedAfterStep1);
    });
  }

  @ParameterizedTest(name = "collapseSequentialCommentsSetting={0}, doExpand={1}, expectExpandedAfterStep1={2}")
  @FieldSource("TWO_STEP_EXPAND_ONE_STEP_COLLAPSE")
  public void testSequentialCommentsTwoStepFolding(boolean collapseSequentialCommentsSetting, boolean doExpand,
                                                   boolean expectExpandedAfterStep1)
    throws Throwable {
    runBare(() -> {
      PythonFoldingSettings.getInstance().COLLAPSE_SEQUENTIAL_COMMENTS = collapseSequentialCommentsSetting;
      checkCollapseExpandAllAction(END_OF_LINE_COMMENT, "multilineComments", doExpand, expectExpandedAfterStep1);
    });
  }

  @ParameterizedTest(name = "collapseTypeAnnotationSetting={0}, doExpand={1}, expectExpandedAfterStep1={2}")
  @FieldSource("TWO_STEP_EXPAND_ONE_STEP_COLLAPSE")
  public void testLongStringsTwoStepFolding(boolean collapseLongStringsSetting, boolean doExpand, boolean expectExpandedAfterStep1)
    throws Throwable {
    runBare(() -> {
      PythonFoldingSettings.getInstance().COLLAPSE_LONG_STRINGS = collapseLongStringsSetting;
      checkCollapseExpandAllAction(STRING_LITERAL_EXPRESSION, "longStringsFolding", doExpand, expectExpandedAfterStep1);
    });
  }

  // PY-76572
  @ParameterizedTest(name = "collapseTypeAnnotationSetting={0}, doExpand={1}, expectExpandedAfterStep1={2}")
  @CsvSource({
    "true, true, false",
    "true, false, false",
    "false, true, true",
    "false, false, true" // last is 'true' due to the two-step collapse feature for type annotations
  })
  public void testTypeAnnotationsTwoStepFolding(boolean collapseTypeAnnotationSetting, boolean doExpand, boolean expectExpandedAfterStep1)
    throws Throwable {
    runBare(() -> {
      PythonFoldingSettings.getInstance().COLLAPSE_TYPE_ANNOTATIONS = collapseTypeAnnotationSetting;
      checkCollapseExpandAllAction(ANNOTATION, "typeAnnotationsTwoStepFolding", doExpand, expectExpandedAfterStep1);
    });
  }

  private void checkCollapseExpandAllAction(@NotNull IElementType elementType,
                                            @NotNull String fileName,
                                            boolean doExpand,
                                            boolean isExpandedAfterStep1) {
    checkCollapseExpandAllAction(TokenSet.create(elementType), fileName, doExpand, isExpandedAfterStep1);
  }

  private void checkCollapseExpandAllAction(@NotNull TokenSet tokenSet,
                                            @NotNull String fileName,
                                            boolean doExpand,
                                            boolean isExpandedAfterStep1) {
    final String filePath = getTestDataPathFile(fileName);
    final File verificationFile = new File(filePath);
    final String expectedContent;
    try {
      expectedContent = FileUtil.loadFile(verificationFile);
    }
    catch (IOException e) {
      throw new RuntimeException("filePath=" + filePath, e);
    }
    final String cleanContent = removeFoldingMarkers(expectedContent);
    final String action = doExpand ? "ExpandAllRegions" : "CollapseAllRegions";
    final String antiAction = doExpand ? "CollapseAllRegions" : "ExpandAllRegions";
    myFixture.configureByText(verificationFile.getName(), cleanContent);
    EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());

    // create initial state
    myFixture.performEditorAction(antiAction);
    myFixture.performEditorAction(antiAction);
    checkFoldingStateOfTypeAnnotations(tokenSet, !doExpand);

    // step 1: type annotations are expanded always
    myFixture.performEditorAction(action);
    checkFoldingStateOfTypeAnnotations(tokenSet, isExpandedAfterStep1);
    // step 2
    myFixture.performEditorAction(action);
    checkFoldingStateOfTypeAnnotations(tokenSet, doExpand);
  }

  private void checkFoldingStateOfTypeAnnotations(@NotNull TokenSet tokenSet, boolean doExpand) {
    final String logAction = doExpand ? "expanded: " : "collapsed: ";
    final Editor editor = myFixture.getEditor();
    final FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    assertTrue(regions.length > 0);
    for (FoldRegion region : regions) {
      PsiElement element = EditorFoldingInfo.get(editor).getPsiElement(region);
      IElementType regionElementType = element.getNode().getElementType();
      if (tokenSet.contains(regionElementType)) {
        assertEquals(logAction + element.getText(), doExpand, region.isExpanded());
      }
    }
  }

  // PY-39406
  public void testStringPrefixFolding() {
    doTest();
  }

  // PY-49174
  public void testMatchFolding() {
    doTest();
  }
}
