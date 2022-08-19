// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.FileASTNode;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.stubs.*;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

@TestDataPath("$CONTENT_ROOT/../testData/stubs/")
public class PyStubsTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/stubs/";
  }

  public void testStubStructure() {
    // vfile is problematic, but we need an SDK to check builtins
    final Project project = myFixture.getProject();

    runWithLanguageLevel(
      LanguageLevel.PYTHON26, // we need 2.6+ for @foo.setter
      () -> {
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
    );
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

    WriteCommandAction.writeCommandAction(myFixture.getProject(), fileImpl).run(() -> {
      pyClass.setName("RenamedClass");
      assertEquals("RenamedClass", pyClass.getName());
    });

    StubElement fileStub = fileImpl.getStub();
    assertNull("There should be no stub if file holds tree element", fileStub);

    WriteCommandAction.writeCommandAction(myFixture.getProject(), fileImpl).run(() -> ((SingleRootFileViewProvider)fileImpl.getViewProvider()).onContentReload());
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
    PsiFile psiFile = myFixture.getPsiManager().findFile(sourceFile);
    return (PyFile)psiFile;
  }

  public void testStubIndexMismatch() {
    VirtualFile vFile = myFixture.getTempDirFixture().createFile("foo.py");
    final Project project = myFixture.getProject();
    PsiFileImpl fooPyFile = (PsiFileImpl)PsiManager.getInstance(project).findFile(vFile);
    assertNotNull(fooPyFile);
    final Document fooDocument = fooPyFile.getViewProvider().getDocument();
    assertNotNull(fooDocument);
    final Collection<PyClass> classes = PyClassNameIndex.find("Foo123", project, GlobalSearchScope.allScope(project));
    assertEquals(0, classes.size());
    WriteCommandAction.writeCommandAction(project, fooPyFile).run(() -> fooDocument.setText("class Foo123: pass"));
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(fooDocument);
    documentManager.performForCommittedDocument(fooDocument, () -> {
      fooPyFile.setTreeElementPointer(null);
      //classes = PyClassNameIndex.find("Foo", project, GlobalSearchScope.allScope(project));
      //fooPyFile.unloadContent();
      DumbServiceImpl.getInstance(project).setDumb(true);
      try {
        assertEquals(1, ((PyFile)fooPyFile).getTopLevelClasses().size());
        assertFalse(fooPyFile.isContentsLoaded());
      }
      finally {
        DumbServiceImpl.getInstance(project).setDumb(false);
      }
      final Collection<PyClass> committedClasses = PyClassNameIndex.find("Foo123", project, GlobalSearchScope.allScope(project));
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
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      final PyFile file = getTestFile();
      final PyClass c = file.findTopLevelClass("C");
      assertNotNull(c);
      final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
      assertNotNull(c.getMetaClassType(false, context));
      final PyClass d = file.findTopLevelClass("D");
      assertNotNull(d);
      assertNotNull(d.getMetaClassType(false, context));
      assertNotParsed(file);
    });
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

  // PY-29983
  public void testNamedTupleNameKeyword() {
    doTestNamedTupleArguments();
  }

  public void testNamedTupleFieldsReference() {
    doTestNamedTupleArguments();
  }

  // PY-29983
  public void testNamedTupleFieldsKeyword() {
    doTestNamedTupleArguments();
  }

  public void testNamedTupleNameChain() {
    doTestNamedTupleArguments();
  }

  public void testNamedTupleFieldsChain() {
    doTestNamedTupleArguments();
  }

  public void testImportedNamedTupleName() {
    doTestUnsupportedNamedTuple(getTestFile());
  }

  public void testImportedNamedTupleFields() {
    doTestUnsupportedNamedTuple(getTestFile());
  }

  public void testFullyQualifiedTypingNamedTuple() {
    doTestTypingNamedTuple(
      QualifiedName.fromDottedString("typing.NamedTuple")
    );
  }

  public void testFullyQualifiedTypingNamedTupleWithAs() {
    doTestTypingNamedTuple(
      QualifiedName.fromDottedString("T.NamedTuple")
    );
  }

  public void testImportedTypingNamedTuple() {
    doTestTypingNamedTuple(
      QualifiedName.fromComponents("NamedTuple")
    );
  }

  public void testImportedTypingNamedTupleWithAs() {
    doTestTypingNamedTuple(
      QualifiedName.fromComponents("NT")
    );
  }

  public void testTypingNamedTupleNameReference() {
    doTestTypingNamedTupleArguments();
  }

  // PY-34134
  public void testTypingNamedTupleNameKeyword() {
    doTestTypingNamedTupleArguments();
  }

  public void testTypingNamedTupleFieldsReference() {
    doTestTypingNamedTupleArguments();
  }

  // PY-34134
  public void testTypingNamedTupleFieldsKeyword() {
    doTestTypingNamedTupleArguments();
  }

  public void testTypingNamedTupleNameChain() {
    doTestTypingNamedTupleArguments();
  }

  public void testTypingNamedTupleFieldsChain() {
    doTestTypingNamedTupleArguments();
  }

  public void testImportedTypingNamedTupleName() {
    doTestUnsupportedTypingNamedTuple(getTestFile());
  }

  public void testImportedTypingNamedTupleFields() {
    doTestUnsupportedTypingNamedTuple(getTestFile());
  }

  public void testFullyQualifiedTypingNamedTupleKwargs() {
    doTestTypingNamedTuple(
      QualifiedName.fromDottedString("typing.NamedTuple")
    );
  }

  public void testFullyQualifiedTypingNamedTupleKwargsWithAs() {
    doTestTypingNamedTuple(
      QualifiedName.fromDottedString("T.NamedTuple")
    );
  }

  public void testImportedTypingNamedTupleKwargs() {
    doTestTypingNamedTuple(
      QualifiedName.fromComponents("NamedTuple")
    );
  }

  public void testImportedTypingNamedTupleKwargsWithAs() {
    doTestTypingNamedTuple(
      QualifiedName.fromComponents("NT")
    );
  }

  public void testTypingNamedTupleKwargsNameReference() {
    doTestTypingNamedTupleArguments();
  }

  public void testTypingNamedTupleKwargsNameChain() {
    doTestTypingNamedTupleArguments();
  }

  public void testImportedTypingNamedTupleKwargsName() {
    doTestUnsupportedTypingNamedTuple(getTestFile());
  }

  public void testImportedTypingNamedTupleKwargsFields() {
    doTestUnsupportedTypingNamedTuple(getTestFile());
  }

  private void doTestNamedTuple(@NotNull QualifiedName expectedCalleeName) {
    doTestNamedTuple("name", Collections.singletonList("field"), Collections.singletonList(null), expectedCalleeName);
  }

  private void doTestTypingNamedTuple(@NotNull QualifiedName expectedCalleeName) {
    doTestNamedTuple("name", Collections.singletonList("field"), Collections.singletonList("str"), expectedCalleeName);
  }

  private void doTestNamedTupleArguments() {
    doTestNamedTuple("name", Arrays.asList("x", "y"), Arrays.asList(null, null), QualifiedName.fromComponents("namedtuple"));
  }

  private void doTestTypingNamedTupleArguments() {
    doTestNamedTuple("name", Arrays.asList("x", "y"), Arrays.asList("str", "int"), QualifiedName.fromComponents("NamedTuple"));
  }

  private void doTestNamedTuple(@NotNull String expectedName,
                                @NotNull List<String> expectedFieldsNames,
                                @NotNull List<String> expectedFieldsTypes,
                                @NotNull QualifiedName expectedCalleeName) {
    final PyFile file = getTestFile();

    final PyTargetExpression attribute = file.findTopLevelAttribute("nt");
    assertNotNull(attribute);

    final PyNamedTupleStub stub = attribute.getStub().getCustomStub(PyNamedTupleStub.class);
    assertNotNull(stub);
    assertEquals(expectedCalleeName, stub.getCalleeName());

    final PyType typeFromStub = TypeEvalContext.codeInsightFallback(myFixture.getProject()).getType(attribute);
    doTestNamedTuple(expectedName, expectedFieldsNames, expectedFieldsTypes, typeFromStub);
    assertNotParsed(file);

    final FileASTNode astNode = file.getNode();
    assertNotNull(astNode);

    final PyType typeFromAst = TypeEvalContext.userInitiated(myFixture.getProject(), file).getType(attribute);
    doTestNamedTuple(expectedName, expectedFieldsNames, expectedFieldsTypes, typeFromAst);
  }

  private void doTestUnsupportedNamedTuple(@NotNull PsiElement anchor) {
    doTestUnsupportedNT(
      (typeFromAst, context) -> {
        assertInstanceOf(typeFromAst, PyCallableType.class);

        final PyType returnType = ((PyCallableType)typeFromAst).getReturnType(context);
        assertEquals(PyBuiltinCache.getInstance(anchor).getTupleType(), returnType);
      }
    );
  }

  private void doTestUnsupportedTypingNamedTuple(@NotNull PsiElement anchor) {
    final PsiElement member = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(PyTypingTypeProvider.NAMEDTUPLE, anchor);
    assertInstanceOf(member, PyClass.class);

    doTestUnsupportedNT(
      (typeFromAst, context) -> {
        assertInstanceOf(typeFromAst, PyClassType.class);
        assertEquals(member, ((PyClassType)typeFromAst).getPyClass());
      }
    );
  }

  private void doTestUnsupportedNT(@NotNull BiConsumer<? super PyType, ? super TypeEvalContext> typeFromAstChecker) {
    final PyFile file = getTestFile();

    final PyTargetExpression attribute = file.findTopLevelAttribute("nt");
    assertNotNull(attribute);

    final PyType typeFromStub = TypeEvalContext.codeInsightFallback(myFixture.getProject()).getType(attribute);
    assertNull(typeFromStub);
    assertNotParsed(file);

    final FileASTNode astNode = file.getNode();
    assertNotNull(astNode);

    final TypeEvalContext context = TypeEvalContext.userInitiated(myFixture.getProject(), file);
    typeFromAstChecker.accept(context.getType(attribute), context);
  }

  private static void doTestNamedTuple(@NotNull String expectedName,
                                       @NotNull List<String> expectedFieldsNames,
                                       @NotNull List<String> expectedFieldsTypes,
                                       @Nullable PyType type) {
    assertInstanceOf(type, PyNamedTupleType.class);

    final PyNamedTupleType namedTupleType = (PyNamedTupleType)type;

    assertEquals(expectedName, namedTupleType.getName());

    final Iterator<String> fieldsNamesIterator = expectedFieldsNames.iterator();
    final Iterator<String> fieldsTypesIterator = expectedFieldsTypes.iterator();

    for (Map.Entry<String, PyNamedTupleType.FieldTypeAndDefaultValue> entry : namedTupleType.getFields().entrySet()) {
      assertTrue(fieldsNamesIterator.hasNext());
      assertTrue(fieldsTypesIterator.hasNext());

      final String fieldName = entry.getKey();
      final PyNamedTupleType.FieldTypeAndDefaultValue fieldTypeAndDefaultValue = entry.getValue();

      assertEquals(fieldsNamesIterator.next(), fieldName);

      final PyType fieldType = fieldTypeAndDefaultValue.getType();
      assertEquals(fieldsTypesIterator.next(), fieldType == null ? null : fieldType.getName());
      assertNull(fieldTypeAndDefaultValue.getDefaultValue());
    }

    assertFalse(fieldsNamesIterator.hasNext());
    assertFalse(fieldsTypesIterator.hasNext());
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
    final PyFile current = getTestFile(getTestName(true) + "/main.py");
    final PyFile external = getTestFile(getTestName(true) + "/lib.py");
    final PyTargetExpression attr = current.findTopLevelAttribute("x");
    assertNotNull(attr);
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), current);
    assertType("int", attr, context);
    assertNotParsed(external);
  }

  // PY-18816
  public void testParameterAnnotation() {
    final PyFile file = getTestFile();
    final PyFunction func = file.findTopLevelFunction("func");
    final PyNamedParameter param = func.getParameterList().findParameterByName("x");
    final String annotation = param.getAnnotationValue();
    assertEquals("int", annotation);
    assertNotParsed(file);
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    assertType("int", param, context);
    assertNotParsed(file);
  }

  // PY-18816
  public void testFunctionAnnotation() {
    final PyFile file = getTestFile();
    final PyFunction func = file.findTopLevelFunction("func");
    final String annotation = func.getAnnotationValue();
    assertEquals("int", annotation);
    assertNotParsed(file);
    assertType("() -> int", func, TypeEvalContext.codeInsightFallback(myFixture.getProject()));
    assertNotParsed(file);
  }

  // PY-18816
  public void testVariableAnnotation() {
    final PyFile file = getTestFile();
    final PyTargetExpression assignmentTarget = file.findTopLevelAttribute("x");
    final String assignmentAnnotation = assignmentTarget.getAnnotationValue();
    assertEquals("int", assignmentAnnotation);
    assertNotParsed(file);
    assertType("int", assignmentTarget, TypeEvalContext.codeInsightFallback(myFixture.getProject()));
    assertNotParsed(file);

    final PyTargetExpression typeDecTarget = file.findTopLevelAttribute("y");
    final String typeDecAnnotation = typeDecTarget.getAnnotationValue();
    assertEquals("str", typeDecAnnotation);
    assertNotParsed(file);
    assertType("str", typeDecTarget, TypeEvalContext.codeInsightFallback(myFixture.getProject()));
    assertNotParsed(file);
  }

  // PY-18816
  public void testAttributeTypeDeclaration() {
    final PyFile file = getTestFile();
    final PyClass pyClass = file.findTopLevelClass("MyClass");
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    final PyTargetExpression classAttr = pyClass.findClassAttribute("foo", false, context);
    assertType("str", classAttr, context);

    final PyTargetExpression instAttr = pyClass.findInstanceAttribute("bar", false);
    assertType("int", instAttr, context);
    assertNotParsed(file);
  }

  // PY-18816
  public void testTypeAliasInParameterAnnotation() {
    final PyFile file = getTestFile();
    final PyFunction func = file.findTopLevelFunction("func");
    final PyNamedParameter param = func.getParameterList().findParameterByName("x");
    assertType("dict[str, Any]", param, TypeEvalContext.codeInsightFallback(myFixture.getProject()));
    assertNotParsed(file);
  }

  // PY-18816
  public void testTypeAliasStubs() {
    final PyFile file = getTestFile();
    final List<PyTargetExpression> attributes = file.getTopLevelAttributes();
    for (PyTargetExpression attr : attributes) {
      assertHasTypingAliasStub(attr.getName().endsWith("_ok"), attr);
    }

    final PyTargetExpression referenceAlias = file.findTopLevelAttribute("plain_ref");
    final PyTargetExpressionStub referenceAliasStub = referenceAlias.getStub();
    assertEquals(PyTargetExpressionStub.InitializerType.ReferenceExpression, referenceAliasStub.getInitializerType());
    assertEquals(QualifiedName.fromDottedString("foo.bar.baz"), referenceAliasStub.getInitializer());

    final PyClass pyClass = file.findTopLevelClass("C");
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    final PyTargetExpression classAttr = pyClass.findClassAttribute("class_attr", false, context);
    assertHasTypingAliasStub(false, classAttr);

    final PyTargetExpression instanceAttr = pyClass.findInstanceAttribute("inst_attr", false);
    assertHasTypingAliasStub(false, instanceAttr);
    assertNotParsed(file);
  }

  // PY-18866
  public void testUnresolvedTypingSymbol() {
    final PyFile file = getTestFile();
    final PyFunction func = file.findTopLevelFunction("func");
    assertType("() -> Any", func, TypeEvalContext.codeInsightFallback(file.getProject()));
    assertNotParsed(file);
  }

  @Nullable
  private static PyTypingAliasStub getAliasStub(@NotNull PyTargetExpression targetExpression) {
    final PyTargetExpressionStub stub = targetExpression.getStub();
    return stub != null ? stub.getCustomStub(PyTypingAliasStub.class) : null;
  }

  private static void assertHasTypingAliasStub(boolean has, @NotNull PyTargetExpression expression) {
    final String message = "Target '" + expression.getName() + "' should " + (has ? "" : "not ") + "have typing alias stub";
    final PyTypingAliasStub stub = getAliasStub(expression);
    if (has) {
      assertNotNull(message, stub);
    }
    else {
      assertNull(message, stub);
    }
  }

  // PY-25655
  public void testBaseClassText() {
    final PyFile file = getTestFile();
    final PyClass pyClass = file.findTopLevelClass("Class");
    final PyClassStub stub = pyClass.getStub();
    assertNotNull(stub);
    final List<String> genericBases = stub.getSuperClassesText();
    assertContainsOrdered(genericBases, "Generic[T, V]", "BaseClass1", "SomeModule.SomeClass");
    assertNotParsed(file);
  }

  // PY-18816
  public void testComplexGenericType() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    final PsiManager manager = PsiManager.getInstance(myFixture.getProject());
    final PyFile originFile = (PyFile)manager.findFile(myFixture.findFileInTempDir("a.py"));
    final PyFile libFile = (PyFile)manager.findFile(myFixture.findFileInTempDir("mod.py"));

    final PyTargetExpression instance = originFile.findTopLevelAttribute("expr");
    assertType("tuple[int, None, str]", instance, TypeEvalContext.codeAnalysis(myFixture.getProject(), originFile));
    assertNotParsed(libFile);
  }

  // PY-24969
  public void testFunctionStubDoesNotContainLocalVariableAnnotation() {
    final PyFile file = getTestFile();
    final PyFunction func = file.findTopLevelFunction("func");
    final PyFunctionStub funcStub = func.getStub();
    assertNull(funcStub.findChildStubByType(PyElementTypes.ANNOTATION));
  }

  // PY-26163
  public void testClassAttributeTypeDeclaration() {
    final PyFile file = getTestFile();
    final PyClass pyClass = file.findTopLevelClass("MyClass");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(file.getProject(), file);

    assertNotNull(pyClass.findClassAttribute("foo", false, context));
    assertNotParsed(file);

    //noinspection ResultOfMethodCallIgnored
    pyClass.getText();
    assertNotNull(pyClass.findClassAttribute("foo", false, context));
  }

  // PY-27398
  public void testDataclassField() {
    final PyFile file1 = getTestFile("dataclassField/a.py");
    final PyFile file3 = getTestFile("dataclassField/b.py");

    final DataclassFieldChecker checker = new DataclassFieldChecker(file1.findTopLevelClass("A"));
    checker.check("a", true, false, true);
    checker.check("b", false, true, true);
    checker.check("c", false, false, false);
    checker.check("d", false, false, true);
    checker.check("e", false, false, true); // fallback `init` value
    checker.check("f", false, false, true); // fallback `init` value
    checker.check("g", false, false, true); // fallback `init` value
    checker.check("h", false, false, true);
    checker.check("i", false, false, true);

    assertNotParsed(file1);
    assertNotParsed(file3);
  }

  // PY-26354
  public void testAttrsField() {
    final PyFile file = getTestFile();

    DataclassFieldChecker checker = new DataclassFieldChecker(file.findTopLevelClass("A"));
    checker.check("a", true, false, true);
    checker.check("b", false, true, true);
    checker.check("c", false, false, true);
    checker.check("d", false, false, false);
    checker.check("e", false, false, false);
    checker.check("f", false, false, false);
    checker.check("g", false, false, true);
    checker.check("h", false, true, true);
    checker.check("i", false, false, true);

    checker = new DataclassFieldChecker(file.findTopLevelClass("B"));
    checker.check("a", true, false, true);
    checker.check("b", false, true, true);
    checker.check("c", false, false, true);
    checker.check("d", false, false, false);
    checker.check("e", false, false, false);
    checker.check("f", false, false, false);
    checker.check("g", false, false, true);
    checker.check("h", false, true, true);
    checker.check("i", false, false, true);

    assertNotParsed(file);
  }

  // PY-34374
  public void testAttrsKwOnlyOnClass() {
    final PyFile file = getTestFile();

    assertTrue(file.findTopLevelClass("Foo1").getStub().getCustomStub(PyDataclassStub.class).kwOnly());
    assertFalse(file.findTopLevelClass("Foo2").getStub().getCustomStub(PyDataclassStub.class).kwOnly());
    assertFalse(file.findTopLevelClass("Foo3").getStub().getCustomStub(PyDataclassStub.class).kwOnly());

    assertNotParsed(file);
  }

  // PY-33189
  public void testAttrsKwOnlyOnField() {
    final PyFile file = getTestFile();
    final PyClass cls = file.findTopLevelClass("Foo");

    assertFalse(cls.findClassAttribute("bar1", false, null).getStub().getCustomStub(PyDataclassFieldStub.class).kwOnly());
    assertTrue(cls.findClassAttribute("bar2", false, null).getStub().getCustomStub(PyDataclassFieldStub.class).kwOnly());
    assertFalse(cls.findClassAttribute("bar3", false, null).getStub().getCustomStub(PyDataclassFieldStub.class).kwOnly());

    assertNotParsed(file);
  }

  // PY-27398
  public void testTypingNewType() {
    final PyFile file = getTestFile("typingNewType/new_type.py");

    final PyTargetExpression type = file.findTopLevelAttribute("UserId");
    final PyTypingNewTypeStub stub = type.getStub().getCustomStub(PyTypingNewTypeStub.class);

    assertNotNull(stub);
    assertEquals("UserId", stub.getName());
    assertEquals("int", stub.getClassType());

    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    final PyType typeDef = context.getType(type);

    assertTrue(typeDef instanceof PyTypingNewType);
    assertNotParsed(file);
  }

  // PY-28879
  public void testVariableTypeCommentWithTupleType() {
    final PyFile file = getTestFile();
    final PyTargetExpression target = file.findTopLevelAttribute("var");
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(target.getProject());
    context.getType(target);
    assertNotParsed(file);
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    final PyFile file = getTestFile();

    final PyParameter[] parameters = file.findTopLevelFunction("f").getParameterList().getParameters();
    assertSize(5, parameters);
    assertInstanceOf(parameters[0], PyNamedParameter.class);
    assertInstanceOf(parameters[1], PySlashParameter.class);
    assertInstanceOf(parameters[2], PyNamedParameter.class);
    assertInstanceOf(parameters[3], PySingleStarParameter.class);
    assertInstanceOf(parameters[4], PyNamedParameter.class);

    assertNotParsed(file);
  }

  // PY-33886
  public void testAssignmentExpressionInComprehension() {
    final PyFile file = getTestFile();
    assertNotNull(file.findTopLevelAttribute("total"));
    assertNotParsed(file);
  }

  // PY-36008
  public void testFullyQualifiedTypedDict() {
    doTestTypingTypedDict(
      QualifiedName.fromDottedString("typing.TypedDict")
    );
  }

  // PY-36008
  public void testFullyQualifiedTypedDictWithAs() {
    doTestTypingTypedDict(
      QualifiedName.fromDottedString("T.TypedDict")
    );
  }

  // PY-36008
  public void testImportedTypedDict() {
    doTestTypingTypedDict(
      QualifiedName.fromComponents("TypedDict")
    );
  }

  // PY-36008
  public void testImportedTypedDictWithAs() {
    doTestTypingTypedDict(
      QualifiedName.fromComponents("TD")
    );
  }

  // PY-36008
  public void testTypedDictNameReference() {
    doTestTypingTypedDictArguments();
  }

  // PY-36008
  public void testTypedDictNameKeyword() {
    doTestTypingTypedDictArguments();
  }

  // PY-36008
  public void testTypedDictFieldsKeyword() {
    doTestTypingTypedDictArguments();
  }

  // PY-41305
  public void testDecoratorQualifiedNames() {
    final PyFile file = getTestFile();
    final PyFunction func = file.findTopLevelFunction("func");
    assertNotNull(func);

    PyDecoratorList decorators = func.getDecoratorList();
    assertNotNull(decorators);
    List<@Nullable String> names = ContainerUtil.map(decorators.getDecorators(),
                                                     d -> ObjectUtils.doIfNotNull(d.getQualifiedName(), QualifiedName::toString));

    assertEquals(names, Arrays.asList(null, "foo", "foo", "foo.bar", "foo.bar", null, null, null));
    assertNotParsed(file);
  }

  private void doTestTypingTypedDictArguments() {
    doTestTypedDict("name", Arrays.asList("x", "y"), Arrays.asList("str", "int"), QualifiedName.fromComponents("TypedDict"));
  }

  private void doTestTypingTypedDict(@NotNull QualifiedName expectedCalleeName) {
    doTestTypedDict("name", Collections.singletonList("field"), Collections.singletonList("str"), expectedCalleeName);
  }

  private void doTestTypedDict(@NotNull String expectedName,
                               @NotNull List<String> expectedFieldsNames,
                               @NotNull List<String> expectedFieldsTypes,
                               @NotNull QualifiedName expectedCalleeName) {
    final PyFile file = getTestFile();

    final PyTargetExpression attribute = file.findTopLevelAttribute("td");
    assertNotNull(attribute);

    final PyTypedDictStub stub = attribute.getStub().getCustomStub(PyTypedDictStub.class);
    assertNotNull(stub);
    assertEquals(expectedCalleeName, stub.getCalleeName());

    final PyType typeFromStub = TypeEvalContext.codeInsightFallback(myFixture.getProject()).getType(attribute);
    doTestTypedDict(expectedName, expectedFieldsNames, expectedFieldsTypes, typeFromStub);
    assertNotParsed(file);

    final FileASTNode astNode = file.getNode();
    assertNotNull(astNode);

    final PyType typeFromAst = TypeEvalContext.userInitiated(myFixture.getProject(), file).getType(attribute);
    doTestTypedDict(expectedName, expectedFieldsNames, expectedFieldsTypes, typeFromAst);
  }

  private static void doTestTypedDict(@NotNull String expectedName,
                                      @NotNull List<String> expectedFieldsNames,
                                      @NotNull List<String> expectedFieldsTypes,
                                      @Nullable PyType type) {
    assertInstanceOf(type, PyTypedDictType.class);
    final PyTypedDictType typedDictType = (PyTypedDictType)type;

    assertEquals(expectedName, typedDictType.getName());

    final Iterator<String> fieldsNamesIterator = expectedFieldsNames.iterator();
    final Iterator<String> fieldsTypesIterator = expectedFieldsTypes.iterator();

    for (Map.Entry<String, PyTypedDictType.FieldTypeAndTotality> entry : typedDictType.getFields().entrySet()) {
      assertTrue(fieldsNamesIterator.hasNext());
      assertTrue(fieldsTypesIterator.hasNext());

      final String fieldName = entry.getKey();
      final PyTypedDictType.FieldTypeAndTotality fieldTypeAndTotality = entry.getValue();

      assertEquals(fieldsNamesIterator.next(), fieldName);

      final PyType fieldType = fieldTypeAndTotality.getType();
      assertEquals(fieldsTypesIterator.next(), fieldType == null ? null : fieldType.getName());
      assertFalse(fieldTypeAndTotality.isRequired());
    }

    assertFalse(fieldsNamesIterator.hasNext());
    assertFalse(fieldsTypesIterator.hasNext());
  }

  private static final class DataclassFieldChecker {

    @NotNull
    private final PyClass myClass;

    private DataclassFieldChecker(@NotNull PyClass cls) {
      myClass = cls;
    }

    private void check(@NotNull String name, boolean hasDefault, boolean hasDefaultFactory, boolean initValue) {
      final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myClass.getProject());
      final PyTargetExpression field = myClass.findClassAttribute(name, false, context);

      final PyDataclassFieldStub fieldStub = field.getStub().getCustomStub(PyDataclassFieldStub.class);
      assertNotNull(fieldStub);

      assertEquals(hasDefault, fieldStub.hasDefault());
      assertEquals(hasDefaultFactory, fieldStub.hasDefaultFactory());
      assertEquals(initValue, fieldStub.initValue());
    }
  }
}
