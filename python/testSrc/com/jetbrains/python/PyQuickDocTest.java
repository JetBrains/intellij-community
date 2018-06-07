// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author dcheryasov
 */
public class PyQuickDocTest extends LightMarkedTestCase {
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
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(myFormat);
    super.tearDown();
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
    final PyDocStringOwner docOwner = (PyDocStringOwner)((PyReferenceExpression)referenceElement).getReference().resolve();
    assertNotNull(docOwner);
    assertEquals(docElement, docOwner.getDocStringExpression());

    checkByHTML(myProvider.generateDoc(docOwner, originalElement));
  }

  private void checkHTMLOnly() {
    final Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
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
    PsiElement referenceElement = originalElement.getParent(); // ident -> expr
    final PsiElement docOwner = ((PyReferenceExpression)referenceElement).getReference().resolve();
    checkByHTML(myProvider.getQuickNavigateInfo(docOwner, referenceElement));
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
    runWithLanguageLevel(
      LanguageLevel.PYTHON26,
      () -> {
        Map<String, PsiElement> marks = loadTest();
        PsiElement referenceElement = marks.get("<the_ref>");
        final PyDocStringOwner docStringOwner = (PyDocStringOwner)referenceElement.getParent().getReference().resolve();
        checkByHTML(myProvider.generateDoc(docStringOwner, referenceElement));
      }
    );
  }

  public void testPropNewDeleter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON26,
      () -> {
        Map<String, PsiElement> marks = loadTest();
        PsiElement referenceElement = marks.get("<the_ref>");
        final PyDocStringOwner docStringOwner = (PyDocStringOwner)((PyReferenceExpression)(referenceElement.getParent())).getReference().resolve();
        checkByHTML(myProvider.generateDoc(docStringOwner, referenceElement));
      }
    );
  }

  public void testPropOldGetter() {
    checkHTMLOnly();
  }


  public void testPropOldSetter() {
    Map<String, PsiElement> marks = loadTest();
    PsiElement referenceElement = marks.get("<the_ref>");
    final PyDocStringOwner docStringOwner = (PyDocStringOwner)referenceElement.getParent().getReference().resolve();
    checkByHTML(myProvider.generateDoc(docStringOwner, referenceElement));
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
    final Map<String, PsiElement> marks = configureByFile(getTestName(false) + "/illegal name.py");
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
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testHomogeneousTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testHeterogeneousTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testUnknownTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testTypeVars() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-28808
  public void testEmptyTupleType() {
    checkHTMLOnly();
  }

  // PY-22730
  public void testOptionalAndUnionTypesContainingTypeVars() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::checkHTMLOnly);
  }

  // PY-22685
  public void testBuiltinLen() {
    checkHTMLOnly();
  }

  public void testArgumentList() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement);
    checkByHTML(myProvider.generateDoc(element, originalElement));
  }

  public void testNotArgumentList() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement);
    assertNull(element);
  }

  public void testDocstring() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement);
    checkByHTML(myProvider.generateDoc(element, originalElement));
  }

  public void testReferenceToMethodQualifiedWithInstance() {
    checkHTMLOnly();
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
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::checkHTMLOnly);
  }

  public void testHoverOverClassWithAllKindSuperClassExpressions() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::checkHover);
  }

  // PY-23247
  public void testOverloads() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-23247
  public void testHoverOverOverloads() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHover);
  }

  // PY-23247
  public void testOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-23247
  public void testHoverOverOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHover);
  }

  // PY-23247
  public void testDocOnImplementationWithOverloads() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PsiElement originalElement = loadTest().get("<the_ref>");
        checkByHTML(myProvider.generateDoc(originalElement.getParent(), originalElement));
      }
    );
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

  // PY-29339
  public void testAsyncFunctionTooltip() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHover);
  }

  // PY-29339
  public void testAsyncFunctionQuickDoc() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-30103
  public void testFunctionWrapping() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-30103
  public void testSingleArgumentMethodNotWrapped() {
    checkHTMLOnly();
  }

  // PY-30103
  public void testReturnTypeWrappedBecauseOfParameters() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-30103
  public void testReturnTypeWrappedBecauseOfFunctionName() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testParamDescriptionsOrder() {
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

  public void testParamDescriptionEpytext() {
    checkHTMLOnly();
  }

  public void testParamAndReturnValueDescriptionNoTagsEpytext() {
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

  // PY-11425
  public void testKeywordArgsDescriptionEpydoc() {
    checkHTMLOnly();
  }

  public void testKeywordArgsDescriptionGoogle() {
    checkHTMLOnly();
  }

  public void testKeywordArgsDescriptionForMissingParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::checkHTMLOnly);
  }

  public void testArgsKwargsTypes() {
    checkHTMLOnly();
  }

  public void testExplicitlyAnnotatedSelfParamType() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::checkHTMLOnly);
  }

  public void testExplicitlyAnnotatedClsParamType() {
    checkHTMLOnly();
  }

  public void testPackage() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    final VirtualFile file = myFixture.findFileInTempDir("pkg/__init__.py");
    final PyFile init = as(PsiManager.getInstance(myFixture.getProject()).findFile(file), PyFile.class);
    checkByHTML(myProvider.generateDoc(init, init));
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/quickdoc/";
  }
}
