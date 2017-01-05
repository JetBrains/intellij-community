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
package com.jetbrains.python;

import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.QualifiedName;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/stubs/")
public class PyStubsTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/stubs/";
  }

  public void testStubStructure() {
    // vfile is problematic, but we need an SDK to check builtins
    final Project project = myFixture.getProject();

    PythonLanguageLevelPusher.setForcedLanguageLevel(project, LanguageLevel.PYTHON26); // we need 2.6+ for @foo.setter
    try {
      final PyFile file = getTestFile();
      final List<PyClass> classes = file.getTopLevelClasses();
      assertEquals(3, classes.size());
      PyClass pyClass = classes.get(0);
      assertEquals("FooClass", pyClass.getName());
      assertEquals("StubStructure.FooClass", pyClass.getQualifiedName());

      final List<PyTargetExpression> attrs = pyClass.getClassAttributes();
      assertEquals(2, attrs.size());
      assertEquals("staticField", attrs.get(0).getName());
      assertTrue(attrs.get(0).getAssignedQName().matches("deco"));

      final PyFunction[] methods = pyClass.getMethods();
      assertEquals(2, methods.length);
      assertEquals("__init__", methods [0].getName());
      assertEquals("fooFunction", methods [1].getName());

      final PyParameter[] parameters = methods[1].getParameterList().getParameters();
      assertFalse(parameters [0].hasDefaultValue());
      assertTrue(parameters [1].hasDefaultValue());

      // decorators
      PyFunction decorated = methods[1];
      PyDecoratorList decos = decorated.getDecoratorList();
      assertNotNull(decos);
      assertNotParsed(file);
      PyDecorator[] da = decos.getDecorators();
      assertNotNull(da);
      assertEquals(1, da.length);
      assertNotParsed(file);
      PyDecorator deco = da[0];
      assertNotNull(deco);
      assertEquals("deco", deco.getName());
      assertNotParsed(file);

      final List<PyTargetExpression> instanceAttrs = pyClass.getInstanceAttributes();
      assertEquals(1, instanceAttrs.size());
      assertEquals("instanceField", instanceAttrs.get(0).getName());

      final List<PyFunction> functions = file.getTopLevelFunctions();
      assertEquals(2, functions.size()); // "deco" and "topLevelFunction"
      PyFunction func = functions.get(0);
      assertEquals("deco", func.getName());

      func = functions.get(1);
      assertEquals("topLevelFunction", func.getName());

      final List<PyTargetExpression> exprs = file.getTopLevelAttributes();
      assertEquals(2, exprs.size());
      assertEquals("top1", exprs.get(0).getName());
      assertEquals("top2", exprs.get(1).getName());

      // properties by call
      pyClass = classes.get(1);
      assertEquals("BarClass", pyClass.getName());

      Property prop = pyClass.findProperty("value", true, null);
      Maybe<PyCallable> maybe_function = prop.getGetter();
      assertTrue(maybe_function.isDefined());
      assertEquals(pyClass.getMethods()[0], maybe_function.value());

      Property setvalueProp = pyClass.findProperty("setvalue", true, null);
      Maybe<PyCallable> setter = setvalueProp.getSetter();
      assertTrue(setter.isDefined());
      assertEquals("__set", setter.value().getName());

      // properties by decorator
      pyClass = classes.get(2);
      assertEquals("BazClass", pyClass.getName());
      prop = pyClass.findProperty("x", true, null);
      maybe_function = prop.getGetter();
      assertTrue(maybe_function.isDefined());
      assertEquals(pyClass.getMethods()[0], maybe_function.value());
      maybe_function = prop.getSetter();
      assertTrue(maybe_function.isDefined());
      assertEquals(pyClass.getMethods()[1], maybe_function.value());

      // ...and the juice:
      assertNotParsed(file);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(project, LanguageLevel.getDefault());
    }
  }

  public void testLoadingDeeperTreeRemainsKnownPsiElement() {
    final PyFile file = getTestFile();
    final List<PyClass> classes = file.getTopLevelClasses();
    assertEquals(1, classes.size());
    PyClass pyClass = classes.get(0);

    assertEquals("SomeClass", pyClass.getName());

    assertNotParsed(file);

    // load the tree now
    final PyStatementList statements = pyClass.getStatementList();
    assertNotNull(((PyFileImpl)file).getTreeElement());

    final PsiElement[] children = file.getChildren();

    assertEquals(1, children.length);
    assertSame(pyClass, children[0]);
  }

  public void testLoadingTreeRetainsKnownPsiElement() {
    final PyFile file = getTestFile();
    final List<PyClass> classes = file.getTopLevelClasses();
    assertEquals(1, classes.size());
    PyClass pyClass = classes.get(0);

    assertEquals("SomeClass", pyClass.getName());

    assertNotParsed(file);

    final PsiElement[] children = file.getChildren(); // Load the tree

    assertNotNull(((PyFileImpl)file).getTreeElement());
    assertEquals(1, children.length);
    assertSame(pyClass, children[0]);
  }

  public void testRenamingUpdatesTheStub() {
    final PyFile file = getTestFile("LoadingTreeRetainsKnownPsiElement.py");
    final List<PyClass> classes = file.getTopLevelClasses();
    assertEquals(1, classes.size());
    final PyClass pyClass = classes.get(0);

    assertEquals("SomeClass", pyClass.getName());

    // Ensure we haven't loaded the tree yet.
    final PyFileImpl fileImpl = (PyFileImpl)file;
    assertNull(fileImpl.getTreeElement());

    final PsiElement[] children = file.getChildren(); // Load the tree

    assertNotNull(fileImpl.getTreeElement());
    assertEquals(1, children.length);
    assertSame(pyClass, children[0]);

    new WriteCommandAction(myFixture.getProject(), fileImpl) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        pyClass.setName("RenamedClass");
        assertEquals("RenamedClass", pyClass.getName());
      }
    }.execute();

    StubElement fileStub = fileImpl.getStub();
    assertNull("There should be no stub if file holds tree element", fileStub);

    new WriteCommandAction(myFixture.getProject(), fileImpl) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        ((SingleRootFileViewProvider)fileImpl.getViewProvider()).onContentReload();
      }
    }.execute();
    assertNull(fileImpl.getTreeElement()); // Test unload succeeded.

    assertEquals("RenamedClass", fileImpl.getTopLevelClasses().get(0).getName());
  }

  public void testImportStatement() {
    final PyFileImpl file = (PyFileImpl) getTestFile();

    final List<PyFromImportStatement> fromImports = file.getFromImports();
    assertEquals(1, fromImports.size());
    final PyFromImportStatement fromImport = fromImports.get(0);
    final PyImportElement[] importElements = fromImport.getImportElements();
    assertEquals(1, importElements.length);
    assertEquals("argv", importElements [0].getVisibleName());
    assertFalse(fromImport.isStarImport());
    assertEquals(0, fromImport.getRelativeLevel());
    final QualifiedName qName = fromImport.getImportSourceQName();
    assertSameElements(qName.getComponents(), "sys");

    final List<PyImportElement> importTargets = file.getImportTargets();
    assertEquals(1, importTargets.size());
    final PyImportElement importElement = importTargets.get(0);
    final QualifiedName importQName = importElement.getImportedQName();
    assertSameElements(importQName.getComponents(), "os", "path");

    assertNotParsed(file);
  }

  public void testDunderAll() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final List<String> all = file.getDunderAll();
    assertSameElements(all, "foo", "bar");
    assertNotParsed(file);
  }

  public void testDynamicDunderAll() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final List<String> all = file.getDunderAll();
    assertNull(all);
    assertNotParsed(file);
  }

  public void testAugAssignDunderAll() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final List<String> all = file.getDunderAll();
    assertNull(all);
    assertNotParsed(file);
  }

  public void testDunderAllAsSum() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final List<String> all = file.getDunderAll();
    assertSameElements(all, "md5", "sha1", "algorithms_guaranteed", "algorithms_available");
    assertNotParsed(file);
  }

  public void testSlots() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final PyClass pyClass = file.getTopLevelClasses().get(0);
    assertSameElements(pyClass.getSlots(null), "foo", "bar");
    assertNotParsed(file);
  }

  public void testImportInTryExcept() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final PsiElement element = file.findExportedName("sys");
    assertTrue(element != null ? element.toString() : "null", element instanceof PyImportElement);
    assertNotParsed(file);
  }

  public void testNameInExcept() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final PsiElement element = file.findExportedName("md5");
    assertTrue(element != null ? element.toString() : "null", element instanceof PyTargetExpression);
    assertNotParsed(file);
  }

  public void testVariableIndex() {
    getTestFile();
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PyTargetExpression> result = PyVariableNameIndex.find("xyzzy", myFixture.getProject(), scope);
    assertEquals(1, result.size());
    assertEquals(0, PyVariableNameIndex.find("shazam", myFixture.getProject(), scope).size());
    assertEquals(0, PyVariableNameIndex.find("boohoo", myFixture.getProject(), scope).size());
    assertEquals(0, PyVariableNameIndex.find("__all__", myFixture.getProject(), scope).size());
  }

  public void testImportInExcept() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final PsiElement element = file.getElementNamed("tzinfo");
    assertTrue(element != null ? element.toString() : "null", element instanceof PyClass);
    assertNotParsed(file);
  }


  public void testImportFeatures() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    assertTrue(file.hasImportFromFuture(FutureFeature.DIVISION));
    assertTrue(file.hasImportFromFuture(FutureFeature.UNICODE_LITERALS));
    assertNotParsed(file);
  }

  public void testIfNameMain() {  // PY-4008
    final PyFileImpl file = (PyFileImpl) getTestFile();
    ensureVariableNotInIndex("xyzzy");
    assertNotParsed(file);
    file.acceptChildren(new PyRecursiveElementVisitor());  // assert no error on switching from stub to AST
    assertNotNull(file.getTreeElement());
  }

  public void testVariableInComprehension() {  // PY-4029
    ensureVariableNotInIndex("xyzzy");
  }

  public void testWrappedStaticMethod() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final PyClass pyClass = file.getTopLevelClasses().get(0);
    final PyFunction[] methods = pyClass.getMethods();
    assertEquals(1, methods.length);
    final PyFunction.Modifier modifier = methods[0].getModifier();
    assertEquals(PyFunction.Modifier.STATICMETHOD, modifier);
    assertNotParsed(file);
  }

  public void testBuiltinAncestor() {
    final PyFileImpl file = (PyFileImpl) getTestFile();
    final PyClass pyClass = file.getTopLevelClasses().get(0);
    final PyClass cls = pyClass.getAncestorClasses(null).iterator().next();
    assertNotNull(cls);
    assertNotParsed(file);
  }

  private void ensureVariableNotInIndex(String name) {
    getTestFile();
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    Collection<PyTargetExpression> result = PyVariableNameIndex.find(name, myFixture.getProject(), scope);
    assertEquals(0, result.size());
  }

  // ---

  private PyFile getTestFile() {
    return getTestFile(getTestName(false) + ".py");
  }

  private PyFile getTestFile(final String fileName) {
    VirtualFile sourceFile = myFixture.copyFileToProject(fileName);
    assert sourceFile != null;
    PsiFile psiFile = myFixture.getPsiManager().findFile(sourceFile);
    return (PyFile)psiFile;
  }

  public void testStubIndexMismatch() {
    VirtualFile vFile = myFixture.getTempDirFixture().createFile("foo.py");
    final Project project = myFixture.getProject();
    PsiFileImpl fooPyFile = (PsiFileImpl) PsiManager.getInstance(project).findFile(vFile);
    assertNotNull(fooPyFile);
    final Document fooDocument = fooPyFile.getViewProvider().getDocument();
    assertNotNull(fooDocument);
    final Collection<PyClass> classes = PyClassNameIndex.find("Foo", project, GlobalSearchScope.allScope(project));
    assertEquals(0, classes.size());
    new WriteCommandAction.Simple(project, fooPyFile) {
      public void run() {
        fooDocument.setText("class Foo: pass");
      }
    }.execute();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(fooDocument);
    documentManager.performForCommittedDocument(fooDocument, () -> {
      fooPyFile.setTreeElementPointer(null);
      //classes = PyClassNameIndex.find("Foo", project, GlobalSearchScope.allScope(project));
      //fooPyFile.unloadContent();
      DumbServiceImpl.getInstance(project).setDumb(true);
      try {
        assertEquals(1, ((PyFile) fooPyFile).getTopLevelClasses().size());
        assertFalse(fooPyFile.isContentsLoaded());
      }
      finally {
        DumbServiceImpl.getInstance(project).setDumb(false);
      }
      final Collection<PyClass> committedClasses = PyClassNameIndex.find("Foo", project, GlobalSearchScope.allScope(project));
      assertEquals(1, committedClasses.size());
    });
  }

  public void testTargetExpressionDocString() {
    final PyFile file = getTestFile();
    final PyClass c = file.findTopLevelClass("C");
    assertNotNull(c);
    final PyTargetExpression foo = c.findClassAttribute("foo", false, null);
    final String docString = foo.getDocStringValue();
    assertEquals("Foo docstring.", docString);
  }

  public void testMetaClass() {
    final PyFile file = getTestFile();
    final PyClass c = file.findTopLevelClass("C");
    assertNotNull(c);
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    assertNotNull(c.getMetaClassType(context));
    final PyClass d = file.findTopLevelClass("D");
    assertNotNull(d);
    assertNotNull(d.getMetaClassType(context));
    assertNotParsed(file);
  }

  // PY-18254
  public void testFunctionTypeComment() {
    final PyFile file = getTestFile();
    final PyFunction func = file.findTopLevelFunction("func");
    assertNotNull(func);
    final String annotation = func.getTypeCommentAnnotation();
    assertEquals("(str) -> int", annotation);
    assertNotParsed(file);
  }

  public void testFullyQualifiedNamedTuple() {
    doTestNamedTuple(
      QualifiedName.fromDottedString("collections.namedtuple")
    );
  }

  public void testFullyQualifiedNamedTupleWithAs() {
    doTestNamedTuple(
      QualifiedName.fromDottedString("C.namedtuple")
    );
  }

  public void testImportedNamedTuple() {
    doTestNamedTuple(
      QualifiedName.fromComponents("namedtuple")
    );
  }

  public void testImportedNamedTupleWithAs() {
    doTestNamedTuple(
      QualifiedName.fromComponents("NT")
    );
  }

  public void testNamedTupleFieldsSequence() {
    doTestNamedTupleArguments();
  }

  public void testNamedTupleNameReference() {
    doTestNamedTupleArguments();
  }

  public void testNamedTupleFieldsReference() {
    doTestNamedTupleArguments();
  }

  public void testNamedTupleNameChain() {
    doTestNamedTupleArguments();
  }

  public void testNamedTupleFieldsChain() {
    doTestNamedTupleArguments();
  }

  public void testImportedNamedTupleName() {
    doTestUnsupportedNamedTuple();
  }

  public void testImportedNamedTupleFields() {
    doTestUnsupportedNamedTuple();
  }

  private void doTestNamedTuple(@NotNull QualifiedName expectedCalleeName) {
    doTestNamedTuple("name", Collections.singletonList("field"), expectedCalleeName);
  }

  private void doTestNamedTupleArguments() {
    doTestNamedTuple("name", Arrays.asList("x", "y"), QualifiedName.fromComponents("namedtuple"));
  }

  private void doTestNamedTuple(@NotNull String expectedName,
                                @NotNull List<String> expectedFields,
                                @NotNull QualifiedName expectedCalleeName) {
    final PyFile file = getTestFile();

    final PyTargetExpression attribute = file.findTopLevelAttribute("nt");
    assertNotNull(attribute);

    final PyNamedTupleStub stub = attribute.getStub().getCustomStub(PyNamedTupleStub.class);
    assertNotNull(stub);
    assertEquals(expectedCalleeName, stub.getCalleeName());

    final PyType typeFromStub = TypeEvalContext.codeInsightFallback(myFixture.getProject()).getType(attribute);
    doTestNamedTuple(expectedName, expectedFields, typeFromStub);
    assertNotParsed(file);

    final FileASTNode astNode = file.getNode();
    assertNotNull(astNode);

    final PyType typeFromAst = TypeEvalContext.userInitiated(myFixture.getProject(), file).getType(attribute);
    doTestNamedTuple(expectedName, expectedFields, typeFromAst);
  }

  private void doTestUnsupportedNamedTuple() {
    final PyFile file = getTestFile();

    final PyTargetExpression attribute = file.findTopLevelAttribute("nt");
    assertNotNull(attribute);

    final PyType typeFromStub = TypeEvalContext.codeInsightFallback(myFixture.getProject()).getType(attribute);
    assertNull(typeFromStub);
    assertNotParsed(file);

    final FileASTNode astNode = file.getNode();
    assertNotNull(astNode);

    final PyType typeFromAst = TypeEvalContext.userInitiated(myFixture.getProject(), file).getType(attribute);
    assertNull(typeFromAst);
  }

  private static void doTestNamedTuple(@NotNull String expectedName,
                                       @NotNull List<String> expectedFields,
                                       @Nullable PyType type) {
    assertInstanceOf(type, PyNamedTupleType.class);

    final PyNamedTupleType namedTupleType = (PyNamedTupleType)type;

    assertEquals(expectedName, namedTupleType.getName());
    assertEquals(expectedFields, namedTupleType.getElementNames());
  }

  // PY-19461
  public void testInheritorsWhenSuperClassImportedWithAs() {
    final PyFile file1 = getTestFile("inheritorsWhenSuperClassImportedWithAs/a.py");
    final PyFile file2 = getTestFile("inheritorsWhenSuperClassImportedWithAs/b.py");

    final Project project = myFixture.getProject();

    final Collection<PyClass> classes =
      StubIndex.getElements(PySuperClassIndex.KEY, "C", project, ProjectScope.getAllScope(project), PyClass.class);

    assertEquals(1, classes.size());
    assertEquals("D", classes.iterator().next().getName());

    assertNotParsed(file1);
    assertNotParsed(file2);
  }

  // PY-19461
  public void testAncestorsWhenSuperClassImportedWithAs() {
    final PyFile file1 = getTestFile("inheritorsWhenSuperClassImportedWithAs/a.py");
    final PyFile file2 = getTestFile("inheritorsWhenSuperClassImportedWithAs/b.py");

    final PyClass pyClass = file2.findTopLevelClass("D");
    assertNotNull(pyClass);

    final Map<QualifiedName, QualifiedName> superClasses = pyClass.getStub().getSuperClasses();
    assertEquals(1, superClasses.size());
    assertEquals(QualifiedName.fromComponents("C"), superClasses.get(QualifiedName.fromComponents("C2")));

    assertNotParsed(file1);
    assertNotParsed(file2);
  }

  public void testAncestorsWhenSuperClassImportedWithQName() {
    final PyFile file1 = getTestFile("ancestorsWhenSuperClassImportedWithQName/a.py");
    final PyFile file2 = getTestFile("ancestorsWhenSuperClassImportedWithQName/b.py");

    final PyClass pyClass = file2.findTopLevelClass("B");
    assertNotNull(pyClass);

    final Map<QualifiedName, QualifiedName> superClasses = pyClass.getStub().getSuperClasses();
    assertEquals(1, superClasses.size());
    assertEquals(QualifiedName.fromDottedString("A.A"), superClasses.get(QualifiedName.fromDottedString("A.A")));

    assertNotParsed(file1);
    assertNotParsed(file2);
  }

  public void testVariableAnnotationsInExternalFiles() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> {
      final PyFile current = getTestFile(getTestName(true) + "/main.py");
      final PyFile external = getTestFile(getTestName(true) + "/lib.py");
      final PyTargetExpression attr = current.findTopLevelAttribute("x");
      assertNotNull(attr);
      final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), current);
      // Will turn into concrete type when we start saving annotations in stubs 
      assertNull(context.getType(attr));
      assertNotParsed(external);
    });
  }
}
