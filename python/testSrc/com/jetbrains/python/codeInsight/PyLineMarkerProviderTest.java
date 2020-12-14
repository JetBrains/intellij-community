/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPossibleClassMember;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;


/**
 * @author Ilya.Kazakevich
 */
public final class PyLineMarkerProviderTest extends PyTestCase {

  /**
   * Checks method has "up" arrow when overrides, and this arrow works
   */
  public void testOverriding() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("spam.py");

    final ASTNode functionNode = myFixture.getElementAtCaret().getNode();
    // We need IDENTIFIER node
    final ASTNode[] functionChildren = functionNode.getChildren(TokenSet.create(PyTokenTypes.IDENTIFIER));
    assert functionChildren.length == 1 : "Wrong number of identifiers: " + functionChildren.length;
    final PsiElement element = functionChildren[0].getPsi();
    LineMarkerInfo<?> lineMarkerInfo = new PyLineMarkerProvider().getLineMarkerInfo(element);
    Assert.assertNotNull("No gutter displayed", lineMarkerInfo);
    GutterIconNavigationHandler<PsiElement> handler = (GutterIconNavigationHandler<PsiElement>)lineMarkerInfo.getNavigationHandler();
    Assert.assertNotNull("Gutter has no navigation handle", handler);
    handler.navigate(new MouseEvent(new JLabel(), 0, 0, 0, 0, 0, 0, false), element);
    final NavigatablePsiElement[] targets = PyLineMarkerNavigator.getNavigationTargets(element);
    Assert.assertNotNull("No navigation targets found", targets);

    Assert.assertThat("Wrong number of targets found", targets, Matchers.arrayWithSize(1));
    final NavigatablePsiElement parentMethod = targets[0];
    Assert.assertThat("Navigation target has wrong type", parentMethod, Matchers.instanceOf(PyPossibleClassMember.class));
    final PyClass parentClass = ((PyPossibleClassMember)parentMethod).getContainingClass();
    Assert.assertNotNull("Function overrides other function, but no parent displayed", parentClass);
    Assert.assertEquals("Wrong parent class name", "Eggs", parentClass.getName());
  }

  // PY-4311
  public void testSeparatorsNotDisplayedForNestedFunctions() {
    doSingleFileLineMarkersTest(lineMarkers -> {
      assertHasNoSeparator(findElementByName("top_level1", PyFunction.class), lineMarkers);
      assertHasSeparator(findElementByName("top_level2", PyFunction.class), lineMarkers);

      assertHasNoSeparator(findElementByName("nested1", PyFunction.class), lineMarkers);
      assertHasNoSeparator(findElementByName("nested2", PyFunction.class), lineMarkers);

      assertHasNoSeparator(findElementByName("method1", PyFunction.class), lineMarkers);
      assertHasSeparator(findElementByName("method2", PyFunction.class), lineMarkers);

      assertHasNoSeparator(findElementByName("nested_in_method1", PyFunction.class), lineMarkers);
      assertHasNoSeparator(findElementByName("nested_in_method2", PyFunction.class), lineMarkers);

      assertHasNoSeparator(findElementByName("method_of_nested_class1", PyFunction.class), lineMarkers);
      assertHasNoSeparator(findElementByName("method_of_nested_class2", PyFunction.class), lineMarkers);
    });
  }

  // PY-4311
  public void testSeparatorsNotDisplayedForNestedClasses() {
    doSingleFileLineMarkersTest(lineMarkers -> {
      assertHasNoSeparator(findElementByName("TopLevel1", PyClass.class), lineMarkers);
      assertHasSeparator(findElementByName("TopLevel2", PyClass.class), lineMarkers);

      assertHasNoSeparator(findElementByName("NestedInMethod1", PyClass.class), lineMarkers);
      assertHasNoSeparator(findElementByName("NestedInMethod2", PyClass.class), lineMarkers);

      assertHasNoSeparator(findElementByName("NestedInFunction1", PyClass.class), lineMarkers);
      assertHasNoSeparator(findElementByName("NestedInFunction2", PyClass.class), lineMarkers);

      assertHasNoSeparator(findElementByName("NestedInClass1", PyClass.class), lineMarkers);
      assertHasNoSeparator(findElementByName("NestedInClass2", PyClass.class), lineMarkers);
    });
  }

  public void testLineMarkersOnDecoratedDeclarations() {
    doSingleFileLineMarkersTest(lineMarkers -> {
      assertHasNoSeparator(findElementByName("decorator", PyFunction.class), lineMarkers);
      assertHasSeparator(findElementByName("MyClass", PyClass.class), lineMarkers);
      assertHasSeparator(findElementByName("func", PyFunction.class), lineMarkers);
    });
  }

  private void doSingleFileLineMarkersTest(@SuppressWarnings("BoundedWildcard") Consumer<List<LineMarkerInfo<?>>> consumer) {
    myFixture.configureByFile(getTestName(false) + ".py");
    final DaemonCodeAnalyzerSettings analyzer = DaemonCodeAnalyzerSettings.getInstance();
    analyzer.SHOW_METHOD_SEPARATORS = true;
    try {
      myFixture.doHighlighting();
      final Document document = myFixture.getEditor().getDocument();
      final List<LineMarkerInfo<?>> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, myFixture.getProject());
      consumer.consume(lineMarkers);
    }
    finally {
      analyzer.SHOW_METHOD_SEPARATORS = false;
    }
  }


  @Nullable
  private <T extends PsiNamedElement> T findElementByName(@NotNull String name, @NotNull Class<? extends T> cls) {
    return SyntaxTraverser
      .psiTraverser(myFixture.getFile())
      .filter(cls)
      .filter(e -> name.equals(e.getName()))
      .first();
  }

  private static void assertHasNoSeparator(@NotNull PsiElement element, @NotNull List<LineMarkerInfo<?>> lineMarkers) {
    assertFalse("Element " + element + " shouldn't have a method separator", hasSeparator(element, lineMarkers));
  }

  private static void assertHasSeparator(@NotNull PsiElement element, @NotNull List<LineMarkerInfo<?>> lineMarkers) {
    assertTrue("Element " + element + " should have a method separator", hasSeparator(element, lineMarkers));
  }

  private static boolean hasSeparator(@NotNull PsiElement element, @NotNull List<LineMarkerInfo<?>> lineMarkers) {
    final PsiElement separatorAnchor = PsiTreeUtil.getDeepestFirst(element);
    final LineMarkerInfo<?> marker = ContainerUtil.find(lineMarkers, maker -> maker.getElement() == separatorAnchor);
    return marker != null && marker.separatorPlacement != null;
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/lineMarkers/";
  }
}