// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author dcheryasov
 */
public class Py3QuickDocTest extends LightMarkedTestCase {
  private PythonDocumentationProvider myProvider;
  private DocStringFormat myFormat;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // the provider is stateless, can be reused, as in real life
    myProvider = new PythonDocumentationProvider();
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    myFormat = documentationSettings.getFormat();
    documentationSettings.setFormat(DocStringFormat.PLAIN);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
      documentationSettings.setFormat(myFormat);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void checkByHTML(@NotNull String text) {
    assertSameLinesWithFile(getTestDataPath() + getTestName(false) + ".html", text);
  }

  @Override
  protected Map<String, PsiElement> loadTest() {
    return configureByFile(getTestName(false) + ".py");
  }

  private void checkRefDocPair() {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(2, marks.size());
    final PsiElement originalElement = marks.get("<the_doc>");
    PsiElement docElement = originalElement.getParent(); // ident -> expr
    assertTrue(docElement instanceof PyStringLiteralExpression);
    String stringValue = ((PyStringLiteralExpression)docElement).getStringValue();
    assertNotNull(stringValue);

    PsiElement referenceElement = marks.get("<the_ref>").getParent(); // ident -> expr
    final PyDocStringOwner docOwner = (PyDocStringOwner)referenceElement.getReference().resolve();
    assertNotNull(docOwner);
    assertEquals(docElement, docOwner.getDocStringExpression());

    checkByHTML(myProvider.generateDoc(docOwner, originalElement));
  }

  private void checkHTMLOnly() {
    final Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
    assertNotNull("<the_ref> marker is missing in test data", originalElement);
    final DocumentationManager manager = DocumentationManager.getInstance(myFixture.getProject());
    final PsiElement target = manager.findTargetElement(myFixture.getEditor(),
                                                        originalElement.getTextOffset(),
                                                        myFixture.getFile(),
                                                        originalElement);
    checkByHTML(myProvider.generateDoc(target, originalElement));
  }

  private void checkHover() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
    final PsiElement docOwner = originalElement.getParent().getReference().resolve();
    checkByHTML(myProvider.getQuickNavigateInfo(docOwner, originalElement));
  }

  public void testDirectFunc() {
    checkRefDocPair();
  }

  public void testIndented() {
    checkRefDocPair();
  }

  public void testDirectClass() {
    checkRefDocPair();
  }

  public void testClassConstructor() {
    checkRefDocPair();
  }

  public void testClassUndocumentedConstructor() {
    checkHTMLOnly();
  }

  public void testClassUndocumentedEmptyConstructor() {
    checkHTMLOnly();
  }

  public void testCallFunc() {
    checkRefDocPair();
  }

  public void testModule() {
    checkRefDocPair();
  }

  public void testMethod() {
    checkRefDocPair();
  }

  // PY-3496
  public void testVariable() {
    checkHTMLOnly();
  }

  public void testInheritedMethod() {
    checkHTMLOnly();
  }

  public void testInheritedMethodOfInnerClass() {
    checkHTMLOnly();
  }

  public void testClassDocstringForConstructor() {
    checkHTMLOnly();
  }

  public void testInnerClassDocstringForConstructor() {
    checkHTMLOnly();
  }

  public void testAncestorClassDocstringForConstructor() {
    checkHTMLOnly();
  }

  public void testAncestorInnerClassDocstringForConstructor() {
    checkHTMLOnly();
  }

  public void testPropNewGetter() {
    checkHTMLOnly();
  }

  public void testPropNewSetter() {
    checkHTMLOnly();
  }

  public void testPropNewDeleter() {
    checkHTMLOnly();
  }

  public void testPropOldGetter() {
    checkHTMLOnly();
  }


  public void testPropOldSetter() {
    checkHTMLOnly();
  }

  public void testPropOldDeleter() {
    checkHTMLOnly();
  }

  public void testPropNewDocstringOfGetter() {
    checkHTMLOnly();
  }

  public void testPropOldDocParamOfPropertyCall() {
    checkHTMLOnly();
  }

  public void testPropOldDocstringOfGetter() {
    checkHTMLOnly();
  }

  public void testPropNewUndefinedSetter() {
    checkHTMLOnly();
  }

  public void testPropOldUndefinedSetter() {
    checkHTMLOnly();
  }

  public void testParam() {
    checkHTMLOnly();
  }

  public void testParamOfInnerFunction() {
    checkHTMLOnly();
  }

  public void testParamOfInnerClassMethod() {
    checkHTMLOnly();
  }

  public void testParamOfFunctionInModuleWithIllegalName() {
    doMultiFileCheckByHTML("illegal name.py");
  }

  public void doMultiFileCheckByHTML(@NotNull String activeFilePath) {
    final Map<String, PsiElement> marks = configureByFile(getTestName(false) + "/" + activeFilePath);
    final PsiElement originalElement = marks.get("<the_ref>");
    final DocumentationManager manager = DocumentationManager.getInstance(myFixture.getProject());
    final PsiElement target = manager.findTargetElement(myFixture.getEditor(),
                                                        originalElement.getTextOffset(),
                                                        myFixture.getFile(),
                                                        originalElement);
    checkByHTML(myProvider.generateDoc(target, originalElement));
  }

  public void testParamOfLambda() {
    checkHTMLOnly();
  }

  public void testInstanceAttr() {
    checkHTMLOnly();
  }

  public void testClassAttr() {
    checkHTMLOnly();
  }

  public void testHoverOverClass() {
    checkHover();
  }

  public void testHoverOverFunction() {
    checkHover();
  }

  public void testHoverOverMethod() {
    checkHover();
  }

  public void testHoverOverParameter() {
    checkHover();
  }

  public void testHoverOverControlFlowUnion() {
    checkHover();
  }

  public void testReturnKeyword() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
    checkByHTML(myProvider.generateDoc(originalElement, originalElement));
  }

  // PY-13422
  public void testNumPyOnesDoc() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    checkHover();
  }

  // PY-17705
  public void testOptionalParameterType() {
    checkHTMLOnly();
  }

  public void testHomogeneousTuple() {
    checkHTMLOnly();
  }

  public void testHeterogeneousTuple() {
    checkHTMLOnly();
  }

  public void testUnknownTuple() {
    checkHTMLOnly();
  }

  public void testTypeVars() {
    checkHTMLOnly();
  }

  // PY-28808
  public void testEmptyTupleType() {
    checkHTMLOnly();
  }

  // PY-22730
  public void testOptionalAndUnionTypesContainingTypeVars() {
    checkHTMLOnly();
  }

  // PY-22685
  public void testBuiltinLen() {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(PythonSdkUtil.findPythonSdk(myFixture.getModule()));

    runWithAdditionalFileInLibDir(
      PyBuiltinCache.getBuiltinsFileName(languageLevel),
      """
        def len(p_object): # real signature unknown; restored from __doc__
            ""\"
            len(object) -> integer
           \s
            Return the number of items of a sequence or collection.
            ""\"
            return 0""",
      (__) -> checkHTMLOnly()
    );
  }

  public void testArgumentList() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement,
                                                                        myFixture.getEditor().getCaretModel().getOffset());
    checkByHTML(myProvider.generateDoc(element, originalElement));
  }

  public void testNotArgumentList() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement,
                                                                        myFixture.getEditor().getCaretModel().getOffset());
    assertNull(element);
  }

  public void testDocstring() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement,
                                                                        myFixture.getEditor().getCaretModel().getOffset());
    checkByHTML(myProvider.generateDoc(element, originalElement));
  }

  public void testReferenceToMethodQualifiedWithInstance() {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(PythonSdkUtil.findPythonSdk(myFixture.getModule()));

    runWithAdditionalFileInLibDir(
      PyBuiltinCache.getBuiltinsFileName(languageLevel),
      """
        class list(object):
            def count(self, value): # real signature unknown; restored from __doc__
                ""\" L.count(value) -> integer -- return number of occurrences of value ""\"
                return 0""",
      (__) -> checkHTMLOnly()
    );
  }

  public void testOneDecoratorFunction() {
    checkHTMLOnly();
  }

  public void testHoverOverOneDecoratorFunction() {
    checkHover();
  }

  public void testManyDecoratorsFunction() {
    checkHTMLOnly();
  }

  public void testHoverOverManyDecoratorsFunction() {
    checkHover();
  }

  public void testOneDecoratorClass() {
    checkHTMLOnly();
  }

  public void testHoverOverOneDecoratorClass() {
    checkHover();
  }

  public void testManyDecoratorsClass() {
    checkHTMLOnly();
  }

  public void testHoverOverManyDecoratorsClass() {
    checkHover();
  }

  public void testClassWithAllKindSuperClassExpressions() {
    checkHTMLOnly();
  }

  public void testHoverOverClassWithAllKindSuperClassExpressions() {
    checkHover();
  }

  // PY-23247
  public void testOverloads() {
    checkHTMLOnly();
  }

  // PY-23247
  public void testHoverOverOverloads() {
    checkHover();
  }

  // PY-23247
  public void testOverloadsAndImplementation() {
    checkHTMLOnly();
  }

  // PY-23247
  public void testHoverOverOverloadsAndImplementation() {
    checkHover();
  }

  // PY-23247
  public void testDocOnImplementationWithOverloads() {
    final PsiElement originalElement = loadTest().get("<the_ref>");
    checkByHTML(myProvider.generateDoc(originalElement.getParent(), originalElement));
  }

  public void testPlainTextDocstringsQuotesPlacementDoesntAffectFormatting() {
    runWithDocStringFormat(DocStringFormat.PLAIN, () -> {
      final Map<String, PsiElement> map = loadTest();
      final PsiElement inline = map.get("<ref1>");
      final String inlineDoc = myProvider.generateDoc(assertInstanceOf(inline.getParent(), PyFunction.class), inline);

      final PsiElement framed = map.get("<ref2>");
      final String framedDoc = myProvider.generateDoc(assertInstanceOf(framed.getParent(), PyFunction.class), framed);

      assertEquals(inlineDoc, framedDoc);
      checkByHTML(inlineDoc);
    });
  }

  // PY-14785
  public void testMultilineAssignedValueForTarget() {
    checkHTMLOnly();
  }

  // PY-14785
  public void testUnmatchedAssignedValueForTarget() {
    checkHTMLOnly();
  }

  // PY-14785
  public void testSingleLineAssignedValueForTarget() {
    checkHTMLOnly();
  }

  // PY-42334
  public void testTypeOfExplicitTypeAlias() {
    checkHTMLOnly();
  }

  // PY-29339
  public void testAsyncFunctionTooltip() {
    checkHover();
  }

  // PY-29339
  public void testAsyncFunctionQuickDoc() {
    checkHTMLOnly();
  }

  // PY-30103
  public void testFunctionWrapping() {
    checkHTMLOnly();
  }

  // PY-30103
  public void testSingleArgumentMethodNotWrapped() {
    checkHTMLOnly();
  }

  // PY-30103
  public void testReturnTypeWrappedBecauseOfParameters() {
    checkHTMLOnly();
  }

  // PY-30103
  public void testReturnTypeWrappedBecauseOfFunctionName() {
    checkHTMLOnly();
  }

  public void testParamDescriptionOrder() {
    checkHTMLOnly();
  }

  public void testParamDescriptionEmptyTags() {
    checkHTMLOnly();
  }

  public void testParamDescriptionOnlyTypeTags() {
    checkHTMLOnly();
  }

  public void testParamDescriptionInheritedMismatched() {
    checkHTMLOnly();
  }

  public void testParamAndReturnValueDescriptionNoTagsRest() {
    checkHTMLOnly();
  }

  public void testExceptionDescriptionRest() {
    checkHTMLOnly();
  }

  public void testExceptionDescriptionGoogle() {
    checkHTMLOnly();
  }

  public void testKeywordArgsDescriptionRest() {
    checkHTMLOnly();
  }

  public void testKeywordArgsDescriptionGoogle() {
    checkHTMLOnly();
  }

  public void testKeywordArgsDescriptionForMissingParameter() {
    checkHTMLOnly();
  }

  public void testArgsKwargsTypes() {
    checkHTMLOnly();
  }

  public void testExplicitlyAnnotatedSelfParamType() {
    checkHTMLOnly();
  }

  public void testExplicitlyAnnotatedClsParamType() {
    checkHTMLOnly();
  }

  public void testDocOnOverloadDefinition() {
    checkHTMLOnly();
  }

  public void testPackage() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    final VirtualFile file = myFixture.findFileInTempDir("pkg/__init__.py");
    final PyFile init = as(PsiManager.getInstance(myFixture.getProject()).findFile(file), PyFile.class);
    checkByHTML(myProvider.generateDoc(init, init));
  }

  public void testSingleLetterInheritedDocstring() {
    checkHTMLOnly();
  }

  // PY-30432
  public void testNoExternalDocumentationSection() {
    doMultiFileCheckByHTML("numpy.py");
  }

  // PY-31025
  public void testGoogleDocstringWithReturnValueDescriptionWithoutType() {
    checkHTMLOnly();
  }

  // PY-31148
  public void testSphinxDocstringWithCombinedParamTypeAndDescription() {
    checkHTMLOnly();
  }

  // PY-31033
  public void testDefaultValues() {
    checkHTMLOnly();
  }

  // PY-31074
  public void testClassDocumentationTakenFromConstructor() {
    checkHTMLOnly();
  }

  // PY-31862
  public void testEscapedSummaryOfFunctionDocstringInQuickNavigationInfo() {
    checkHover();
  }

  // PY-31862
  public void testEscapedSummaryOfClassDocstringInQuickNavigationInfo() {
    checkHover();
  }

  // PY-31862
  public void testEscapedSummaryOfConstructorDocstringInQuickNavigationInfo() {
    checkHover();
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    checkHover();
  }

  public void testStandardCollectionTypesRenderedCapitalizedBefore39() {
    checkHTMLOnly();
  }

  // PY-42418
  public void testStandardCollectionTypesRenderedWithOriginalCase() {
    checkHTMLOnly();
  }

  // PY-42418
  public void testTupleTypeIsRenderedLowercased() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testElseInsideIfStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testElseInsideForStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testElseInsideTryExceptStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testElseInsideWhileStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testAsInsideWithStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testAsInsideImportStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testAsInsideExceptStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testFromInsideImportStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testFromInsideYieldStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testFromInsideRaiseStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testInInsideForStatement() {
    checkHTMLOnly();
  }

  // PY-52281
  public void testInInsideIfStatement() {
    checkHTMLOnly();
  }

  // PY-43035
  public void testMultilineReturnSectionGoogle() {
    checkHTMLOnly();
  }

  // PY-43035
  public void testMultilineReturnSectionNumpy() {
    checkHTMLOnly();
  }

  // PY-55521
  public void testTargetExpressionInsideTypeDeclaration() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testClassAndInstanceAttributesInOneSectionGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testClassAndInstanceAttributesInOneSectionNumpy() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testClassAndInstanceAttributesInOneSectionRest() {
    runWithDocStringFormat(DocStringFormat.REST, () -> checkHTMLOnly());
  }

  // PY-33341
  public void testAttributesOrderGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testClassAndInstanceAttributesWithSameNameNotDuplicatedGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testInheritedAttributesGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testAttributesDescriptionFromClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testArgsDescriptionFromClassAndInitNotMixedGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testArgsDescriptionFromClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testArgsFromInitNotTakenInClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testArgsInClassDocGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testInitEmptyDocstringOverClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-33341
  public void testAttributeDescriptionEmptyGoogle() {
    checkHTMLOnly();
  }

  // PY-56416
  public void testInstanceAttributeDescriptionFromClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-56416
  public void testClassAttributeDescriptionFromClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-56416
  public void testClassAttributeDescriptionFromClassDocstringRest() {
    checkHTMLOnly();
  }

  // PY-56416
  public void testInstanceAttributeDescriptionFromClassDocstringRest() {
    checkHTMLOnly();
  }

  // PY-56416
  public void testAttributeDocsNotMixed() {
    checkHTMLOnly();
  }

  // PY-28900
  public void testParameterDescriptionFromClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-28900
  public void testParameterDescriptionFromInitDocstringOverClassDocstringGoogle() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testConcatenateInReturn() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testConcatenateInParam() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testConcatenateSeveralFirstParamInParam() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testConcatenateInGeneric() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testSeveralParamSpecs() {
    checkHTMLOnly();
  }

  // PY-53104
  public void testSelf() {
    checkHTMLOnly();
  }

  // PY-53104
  public void testTypingExtensionsSelf() {
    checkHTMLOnly();
  }

  // PY-64074
  public void testTypeParameter() {
    checkHTMLOnly();
  }

  // PY-64074
  public void testTypeKeyword() {
    checkHTMLOnly();
  }

  // PY-64074
  public void testTypeAliasStatement() {
    checkHTMLOnly();
  }

  // PY-23067
  public void testFunctoolsWraps() {
    checkHTMLOnly();
  }

  public void testExplicitCallableParameterListRendering() {
    checkHTMLOnly();
  }

  public void testInferredCallableParameterListRendering() {
    checkHTMLOnly();
  }

  // PY-77171
  public void testImplicitResolve() {
    checkHTMLOnly();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/quickdoc/";
  }
}
