// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PyClassMROTest extends PyTestCase {
  public void testSimpleDiamond() {
    assertMRO(getClass("C"), "B1", "B2", "object");
  }

  // TypeError in Python
  public void testMROConflict() {
    assertMRO(getClass("C"), "unknown");
  }

  public void testCircularInheritance() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    final String testName = getTestName(false);
    myFixture.configureByFiles(getPath(testName), getPath(testName + "2"));
    final PyClass cls = myFixture.findElementByText("Foo", PyClass.class);
    assertNotNull(cls);
    assertMRO(cls, "unknown");
  }

  public void testExampleFromDoc1() {
    assertMRO(getClass("A"), "B", "C", "D", "E", "F", "object");
  }

  public void testExampleFromDoc2() {
    assertMRO(getClass("A"), "B", "E", "C", "D", "F", "object");
  }

  public void testExampleFromDoc3() {
    assertMRO(getClass("G"), "unknown");
  }

  public void testExampleFromDoc4() {
    assertMRO(getClass("G"), "E", "F", "object");
  }

  public void testSixWithMetaclass() {
    assertMRO(getClass("C"), "B", "D", "object");
  }

  public void testSixWithMetaclassWithAs() {
    assertMRO(getClass("C"), "B", "D", "object");
  }

  // PY-22806
  public void testSixWithMetaclassOnly() {
    assertMRO(getClass("C"), "object");
  }

  // PY-4183
  public void testComplicatedDiamond() {
    assertMRO(getClass("H"), "E", "F", "B", "G", "C", "D", "A", "object");
  }

  public void testTangledInheritance() {
    final int numClasses = 100;

    final List<String> expectedMRO = new ArrayList<>();
    for (int i = numClasses - 1; i >= 1; i--) {
      expectedMRO.add(String.format("Class%03d", i));
    }
    expectedMRO.add("object");
    final PyClass pyClass = getClass(String.format("Class%03d", numClasses));
    final long startTime = System.currentTimeMillis();
    assertMRO(pyClass, ArrayUtilRt.toStringArray(expectedMRO));
    final long elapsed = System.currentTimeMillis() - startTime;
    assertTrue("Calculation of MRO takes too much time: " + elapsed + " ms", elapsed < 1000);
  }

  // PY-11401
  public void testUnresolvedClassesImpossibleToBuildMRO() {
    assertMRO(getClass("ObjectManager"),
              "CopyContainer", "unknown", "Navigation", "unknown", "Tabs", "unknown", "unknown", "unknown", "Collection", "Resource",
              "LockableItem", "EtagSupport", "Traversable", "object", "unknown");
  }

  public void assertMRO(@NotNull PyClass cls, String @NotNull ... mro) {
    final List<PyClassLikeType> types = cls.getAncestorTypes(TypeEvalContext.deepCodeInsight(cls.getProject()));
    final List<String> classNames = new ArrayList<>();
    for (PyClassLikeType type : types) {
      if (type != null) {
        final String name = type.getName();
        if (name != null) {
          classNames.add(name);
          continue;
        }
      }
      classNames.add("unknown");
    }
    assertOrderedEquals(classNames, Arrays.asList(mro));
  }

  public void assertMetaClass(@NotNull PyClass cls, @NotNull String name) {
    final TypeEvalContext context = TypeEvalContext.deepCodeInsight(cls.getProject());
    final PyType metaClassType = cls.getType(context).getMetaClassType(context, true);
    assertInstanceOf(metaClassType, PyClassType.class);
    assertTrue(((PyClassType)metaClassType).isDefinition());
    assertEquals(name, metaClassType.getName());
  }

  // PY-20026
  public void testDuplicatedBaseClasses() {
    assertMRO(getClass("MyClass"), "Base", "object");
  }

  // PY-27656
  public void testDirectlyInstantiatedMetaclassAncestor() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> {
      final PyClass pyClass = getClass("MyClass");
      assertMRO(pyClass, "object");
      assertMetaClass(pyClass, "Meta");
    });
  }

  // PY-27656
  public void testMetaClassDeclaredThroughAncestor() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> {
      final PyClass pyClass = getClass("MyClass");
      assertMRO(pyClass, "Base", "object");
      assertMetaClass(pyClass, "Meta");
    });
  }

  // PY-20026
  public void testUnresolvedMetaClassAncestors() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> {
      final PyClass pyClass = getClass("CompositeFieldMeta");
      assertMRO(pyClass, "object");
      assertMetaClass(pyClass, "type");
    });
  }

  public void testTypingGenericAsFirstBaseClass() {
    PyClass pyClass = getClass("MyClass");
    assertMRO(pyClass, "Base", "Generic", "object");
  }

  // PY-21837
  public void testClassImportedFromUnstubbedFileAndSuperImportedWithAs() {
    myFixture.copyDirectoryToProject("codeInsight/classMRO/" + getTestName(false), "");

    final VirtualFile d = myFixture.findFileInTempDir("D.py");
    final VirtualFile bc = myFixture.findFileInTempDir("BC.py");

    final PyFile dPsi = (PyFile)myFixture.getPsiManager().findFile(d);
    final PsiFile bPsi = myFixture.getPsiManager().findFile(bc);

    //noinspection ResultOfMethodCallIgnored
    bPsi.getNode(); // unstubbing is necessary

    final PyClass dClass = dPsi.findTopLevelClass("D");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), dPsi); // such context is necessary
    final List<PyClass> ancestors = dClass.getAncestorClasses(context);

    assertOrderedEquals(ContainerUtil.map(ancestors, PyClass::getName), Arrays.asList("C", "A", "B", PyNames.OBJECT));
  }

  @NotNull
  private PyClass getClass(@NotNull String name) {
    myFixture.configureByFile(getPath(getTestName(false)));
    final PyClass cls = myFixture.findElementByText(name, PyClass.class);
    assertNotNull(cls);
    return cls;
  }

  private static String getPath(String name) {
    return "codeInsight/classMRO/" + name + ".py";
  }
}
