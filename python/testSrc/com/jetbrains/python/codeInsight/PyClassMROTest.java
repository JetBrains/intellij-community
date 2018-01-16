/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.util.ArrayUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author vlan
 */
public class PyClassMROTest extends PyTestCase {
  public void testSimpleDiamond() {
    assertMRO(getClass("C"), "B1", "B2", "object");
  }

  // TypeError in Python
  public void testMROConflict() {
    assertMRO(getClass("C"), "unknown");
  }

  public void testCircularInheritance() {
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
    assertMRO(pyClass, ArrayUtil.toStringArray(expectedMRO));
    final long elapsed = System.currentTimeMillis() - startTime;
    assertTrue("Calculation of MRO takes too much time: " + elapsed + " ms", elapsed < 1000);
  }

  // PY-11401
  public void testUnresolvedClassesImpossibleToBuildMRO() {
    assertMRO(getClass("ObjectManager"),
              "CopyContainer", "unknown", "Navigation", "unknown", "Tabs", "unknown", "unknown", "unknown", "Collection", "Resource",
              "LockableItem", "EtagSupport", "Traversable", "object", "unknown");
  }

  public void assertMRO(@NotNull PyClass cls, @NotNull String... mro) {
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
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> {
      final PyClass pyClass = getClass("CompositeFieldMeta");
      assertMRO(pyClass, "object");
      assertMetaClass(pyClass, "type");
    });
  }

  @NotNull
  public PyClass getClass(@NotNull String name) {
    myFixture.configureByFile(getPath(getTestName(false)));
    final PyClass cls = myFixture.findElementByText(name, PyClass.class);
    assertNotNull(cls);
    return cls;
  }

  private static String getPath(String name) {
    return "codeInsight/classMRO/" + name + ".py";
  }
}
