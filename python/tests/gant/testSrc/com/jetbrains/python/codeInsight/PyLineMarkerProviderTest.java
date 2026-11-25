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

import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.idea.TestFor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.NavigationTestUtil;
import com.intellij.util.Consumer;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.JLabel;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Ilya.Kazakevich
 */
@Subsystems.CodeInsight
@Layers.Functional
public final class PyLineMarkerProviderTest extends PyTestCase {
  private static final int SUBCLASS_COUNT = 20;
  private static final ExtensionPointName<QueryExecutor<PyClass, PyClassInheritorsSearch.SearchParameters>> INHERITORS_SEARCH_EP =
    ExtensionPointName.create("Pythonid.pyClassInheritorsSearch");

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
    NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());
    final Editor editor = FileEditorManager.getInstance(myFixture.getProject()).getSelectedTextEditor();
    Assert.assertNotNull("No editor is open", editor);
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    Assert.assertNotNull("The editor has no file", file);
    Assert.assertEquals("The gutter icon did not navigate to the super method", "eggs.py", file.getName());
    Assert.assertEquals("The caret did not land on Eggs.ham",
                        editor.getDocument().getText().indexOf("ham(self)"), editor.getCaretModel().getOffset());
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

  @TestFor(issues = "PY-92743", classes = PyLineMarkerProvider.class)
  public void testOverridingMethodsSearchStopsAtFirstOverridingSubclass() {
    assertEquals(1, countSubclassesVisitedByOverridingMethodsSearch(false));
  }

  @TestFor(issues = "PY-92743", classes = PyLineMarkerProvider.class)
  public void testOverridingMethodsSearchStopsAtFirstOverridingSubclassWithAnotherClassInBatch() {
    fixme("PY-92743 the search continues while another class in the batch has unmatched methods",
          AssertionError.class, "expected:<1> but was:<" + SUBCLASS_COUNT + ">",
          () -> assertEquals(1, countSubclassesVisitedByOverridingMethodsSearch(true)));
  }

  /**
   * Feeds the subclasses of {@code A} to the slow line marker pass through a masked inheritors search and counts the subclasses
   * the provider takes before it stops the search. Only the first subclass overrides {@code A.m}.
   */
  private int countSubclassesVisitedByOverridingMethodsSearch(boolean withSecondClass) {
    final StringBuilder text = new StringBuilder("""
      class A:
          def m(self): ...
      """);
    if (withSecondClass) {
      text.append("""
        class B:
            def n(self): ...
        """);
    }
    text.append("""
      class A1(A):
          def m(self): ...
      """);
    for (int i = 2; i <= SUBCLASS_COUNT; i++) {
      text.append("class A").append(i).append("(A): ...\n");
    }
    final PyFile file = (PyFile)myFixture.configureByText("a.py", text.toString());
    final PyClass a = file.findTopLevelClass("A");
    final List<PyClass> subclasses = new ArrayList<>();
    for (int i = 1; i <= SUBCLASS_COUNT; i++) {
      subclasses.add(file.findTopLevelClass("A" + i));
    }
    final AtomicInteger visited = new AtomicInteger();
    final QueryExecutor<PyClass, PyClassInheritorsSearch.SearchParameters> executor = (parameters, consumer) -> {
      if (parameters.getSuperClass() != a) return true;
      for (PyClass subclass : subclasses) {
        visited.incrementAndGet();
        if (!consumer.process(subclass)) return false;
      }
      return true;
    };
    ExtensionTestUtil.maskExtensions(INHERITORS_SEARCH_EP, List.of(executor), myFixture.getTestRootDisposable());

    final List<PsiElement> methods = new ArrayList<>();
    methods.add(a.findMethodByName("m", false, null));
    if (withSecondClass) {
      methods.add(file.findTopLevelClass("B").findMethodByName("n", false, null));
    }
    final List<LineMarkerInfo<?>> markers = new ArrayList<>();
    new PyLineMarkerProvider().collectSlowLineMarkers(methods, markers);
    assertEquals("The overriding marker of A.m", 1, markers.size());
    return visited.get();
  }

  private void doSingleFileLineMarkersTest(@SuppressWarnings("BoundedWildcard") Consumer<List<? extends LineMarkerInfo<?>>> consumer) {
    myFixture.configureByFile(getTestName(false) + ".py");
    final DaemonCodeAnalyzerSettings analyzer = DaemonCodeAnalyzerSettings.getInstance();
    analyzer.SHOW_METHOD_SEPARATORS = true;
    try {
      myFixture.doHighlighting();
      final Document document = myFixture.getEditor().getDocument();
      final List<LineMarkerInfo<?>> lineMarkers = LineMarkersPass.getDisplayedLineMarkers(document, myFixture.getProject());
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

  private static void assertHasNoSeparator(@NotNull PsiElement element, @NotNull List<? extends LineMarkerInfo<?>> lineMarkers) {
    assertFalse("Element " + element + " shouldn't have a method separator", hasSeparator(element, lineMarkers));
  }

  private static void assertHasSeparator(@NotNull PsiElement element, @NotNull List<? extends LineMarkerInfo<?>> lineMarkers) {
    assertTrue("Element " + element + " should have a method separator", hasSeparator(element, lineMarkers));
  }

  private static boolean hasSeparator(@NotNull PsiElement element, @NotNull List<? extends LineMarkerInfo<?>> lineMarkers) {
    final PsiElement separatorAnchor = PsiTreeUtil.getDeepestFirst(element);
    final LineMarkerInfo<?> marker = ContainerUtil.find(lineMarkers, maker -> maker.getElement() == separatorAnchor);
    return marker != null && marker.separatorPlacement != null;
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/lineMarkers/";
  }
}