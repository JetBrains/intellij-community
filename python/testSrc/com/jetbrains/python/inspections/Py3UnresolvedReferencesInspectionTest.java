/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Py3UnresolvedReferencesInspectionTest extends PyInspectionTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnresolvedReferencesInspection3K/";

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnresolvedReferencesInspection.class;
  }

  @Override
  protected String getTestCaseDirectory() {
    return TEST_DIRECTORY;
  }

  protected void doMultiFileTest(@NotNull String filename, @NotNull List<String> sourceRoots) {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    final Module module = myFixture.getModule();
    for (String root : sourceRoots) {
      PsiTestUtil.addSourceRoot(module, myFixture.findFileInTempDir(root));
    }
    try {
      final PsiFile currentFile = myFixture.configureFromTempProjectFile(filename);
      myFixture.enableInspections(getInspectionClass());
      myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
      assertProjectFilesNotParsed(currentFile);
      assertSdkRootsNotParsed(currentFile);
    }
    finally {
      for (String root : sourceRoots) {
        PsiTestUtil.removeSourceRoot(module, myFixture.findFileInTempDir(root));
      }
    }
  }

  public void testNamedTuple() {
    doTest();
  }

  public void testNamedTupleAssignment() {
    doMultiFileTest("a.py");
  }

  // TODO: Currently there are no stubs for namedtuple() in the base classes list and no indicators for forcing stub->AST
  public void _testNamedTupleBaseStub() {
    doMultiFileTest("a.py");
  }

  // PY-10208
  public void testMetaclassMethods() {
    doTest();
  }

  // PY-19702
  public void testMetaclassAttribute() {
    doTest();
  }

  // PY-19702
  public void testNonexistentMetaclassAttribute() {
    doTest();
  }

  public void testMetaclassStub() {
    doMultiFileTest("a.py");
    final Project project = myFixture.getProject();
    Collection<PyClass> classes = PyClassNameIndex.find("M", project, GlobalSearchScope.allScope(project));
    for (PyClass cls : classes) {
      final PsiFile file = cls.getContainingFile();
      if (file instanceof PyFile) {
        assertNotParsed(file);
      }
    }
  }

  // PY-9011
  public void testDatetimeDateAttributesOutsideClass() {
    doTest();
  }

  public void testObjectNewAttributes() {
    doTest();
  }

  public void testEnumMemberAttributes() {
    doMultiFileTest("a.py");
  }

  // PY-12864
  public void testAttributesOfUnresolvedTypeFile() {
    doTest();
  }

  // PY-14385
  public void testNotImportedSubmodulesOfNamespacePackage() {
    doMultiFileTest("main.py");
  }

  // PY-15017
  public void testClassLevelReferenceInMethodAnnotation() {
    doTest();
  }

  // PY-17841
  public void testTypingParameterizedTypeIndexing() {
    doTest();
  }

  // PY-17841
  public void testMostDerivedMetaClass() {
    doTest();
  }

  // PY-17841
  public void testNoMostDerivedMetaClass() {
    doTest();
  }

  // PY-19028
  public void testDecodeBytesAfterSlicing() {
    doTest();
  }

  // PY-13734
  public void testDunderClass() {
    doTest();
  }

  // PY-19085
  public void testReAndRegexFullmatch() {
    doTest();
  }

  // PY-19775
  public void testAsyncInitMethod() {
    doTest();
  }

  // PY-19691
  public void testNestedPackageNamedAsSourceRoot() {
    doMultiFileTest("a.py", Collections.singletonList("lib1"));
  }


  //PY-28383
  public void testNamespacePackageInMultipleRoots() {
    doMultiFileTest("a.py", Arrays.asList("root1/src", "root2/src"));
  }

  // PY-18972
  public void testReferencesInFStringLiterals() {
    doTest();
  }

  // PY-11208
  public void testMockPatchObject() {
    runWithAdditionalClassEntryInSdkRoots(
      getTestDirectoryPath() + "/lib",
      () -> {
        final PsiFile file = myFixture.configureByFile(getTestDirectoryPath() + "/a.py");
        configureInspection();
        assertSdkRootsNotParsed(file);
      }
    );
  }

  // PY-22525
  public void testTypingIterableDunderGetItem() {
    doTest();
  }

  // PY-22642
  public void testTypingGenericDunderGetItem() {
    doTest();
  }

  // PY-27102
  public void testTypingGenericIndirectInheritorGetItem() {
    doTest();
  }

  // PY-28177
  public void testTypingOpaqueNameDunderGetItem() {
    doTest();
  }

  // PY-21655
  public void testUsageOfFunctionDecoratedWithAsyncioCoroutine() {
    doMultiFileTest("a.py");
  }

  // PY-21655
  public void testUsageOfFunctionDecoratedWithTypesCoroutine() {
    doMultiFileTest("a.py");
  }

  // PY-22899, PY-22937
  public void testCallTypeGetAttributeAndSetAttrInInheritor() {
    doTest();
  }

  // PY-8936
  public void testDescriptorAttribute() {
    doTest();
  }

  // PY-13273
  public void testComprehensionInDecorator() {
    doTest();
  }

  // PY-28406
  public void testFromNamespacePackageImportInManySourceRoots() {
    doMultiFileTest("a.py", Arrays.asList("root1", "root2"));
  }

  public void testNamespacePackageRedundantUnion() {
    doMultiFileTest("a.py", Arrays.asList("root1", "root2"));
  }

  // PY-18629
  public void testPreferImportedModuleOverNamespacePackage() {
    doMultiFileTest();
  }

  // PY-27964
  public void testUsingFunctoolsSingledispatch() {
    doTest();
  }

  // PY-27866
  public void testUnionOwnSlots() {
    doTestByText("""
                   from typing import Union

                   class A:
                       __slots__ = ['x']

                   class B:
                       __slots__ = ['y']
                      \s
                   def foo(ab: Union[A, B]):
                       print(ab.x)""");
  }

  // PY-37755 PY-2700
  public void testGlobalAndNonlocalUnresolvedAttribute() {
    doTest();
  }

  // PY-44974
  public void testNoInspectionInBitwiseOrUnionNoneInt() {
    doTestByText("print(None | int)");
  }

  // PY-44974
  public void testNoInspectionInBitwiseOrUnionIntNone() {
    doTestByText("print(int | None)");
  }

  // PY-44974
  public void testNoInspectionInBitwiseOrUnionIntStrNone() {
    doTestByText("print(int | str | None)");
  }

  // PY-44974
  public void testNoInspectionInBitwiseOrUnionNoneParIntStr() {
    doTestByText("print(None | (int | str))");
  }

  // PY-44974
  public void testNoInspectionInBitwiseOrUnionWithParentheses() {
    doTestByText("bar: int | ((list | dict) | (float | str)) = \"\"");
  }

  public void testClassLevelDunderAll() {
    doMultiFileTest("a.py");
  }

  // PY-50885
  public void testNamespacePackageReferenceInDocstringType() {
    doMultiFileTest();
  }

  // PY-46257
  public void testNoWarningForTypeGetItem() {
    doTestByText("expr: type[str]");
  }

  // PY-35190
  public void testABCMetaRegisterMethod() {
    doTestByText("""
                 from abc import ABCMeta
                 
                 class MyABC(metaclass=ABCMeta):
                     pass
                 
                 MyABC.register(str)
                 """);
  }

  // PY-54356
  public void testDunderOrResolvedForTypingCallable() {
    doTestByText("""
                   from typing import Any, Callable
                   class C:
                       def a_method(self, key: str, decoder: Callable | None = None) -> Any | None:
                           pass
                   """);
  }

  // PY-77168
  public void testReferenceFromUnderUnmatchedVersionCheck() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      doTestByText("""
                     import sys
                     from typing import overload
                     
                     Alias = int
                     if sys.version_info < (3, 9):
                         @overload
                         def f() -> Alias:
                             ...
                   
                     
                         import enum
                         enum
                     
                     
                         class MyClass:
                             ...
                     
                         var1: MyClass
                         var1
                     
                     
                     if sys.version_info < (3, 7):
                         var1
                         var2: MyClass
                     
                     if sys.version_info >= (3, 11):
                         <error descr="Unresolved reference 'f'">f</error>()
                     
                         <error descr="Unresolved reference 'enum'">enum</error>
                     
                         var3: <error descr="Unresolved reference 'MyClass'">MyClass</error>
                     """);
    });
  }

  public void testNewTypeCannotBeGeneric() {
    doTestByText("""
                 from typing import NewType
                 
                 A = NewType("A", list)
                 a: A<warning>[</warning>int]
                 """);
  }

  // PY-76895
  public void testUnresolvedReferenceReportedIfTypeVarHasBound() {
    doTestByText("""
                 from typing import TypeVar, Generic
                 T = TypeVar("T", bound=str)
                 class Test(Generic[T]):
                    def foo(self, x: T):
                        x.capitalize()
                        x.<warning descr="Cannot find reference 'is_integer' in 'T'">is_integer</warning>()  # E
                 """);
    doTestByText("""
                 class Test[T: str]:
                     def foo(self, x: T):
                         x.capitalize()
                         x.<warning descr="Cannot find reference 'is_integer' in 'T'">is_integer</warning>()  # E
                 """);
  }

  // PY-76895
  public void testUnresolvedReferenceReportedIfTypeVarHasConstraints() {
    doTestByText("""
                 from typing import TypeVar, Generic
                 T = TypeVar("T", int, str)
                 class Test(Generic[T]):
                    def foo(self, x: T):
                        x.capitalize() # OK
                        x.is_integer()  # OK
                        x.<warning descr="Cannot find reference 'hex' in 'T'">hex</warning>()  # E
                 """);
    doTestByText("""
                 class Test[T: (str, int)]:
                     def foo(self, x: T):
                         x.capitalize() # OK
                         x.is_integer()  # OK
                         x.<warning descr="Cannot find reference 'hex' in 'T'">hex</warning>()  # E
                 """);

  }

  // PY-76895
  public void testUnresolvedReferenceReportedIfTypeVarHasDefault() {
    doTestByText("""
                 from typing import TypeVar, Generic
                 T = TypeVar("T", default=str)
                 class Test(Generic[T]):
                    def foo(self, x: T):
                        x.capitalize()
                        x.<warning descr="Cannot find reference 'is_integer' in 'T'">is_integer</warning>()  # E
                 """);
    doTestByText("""
                 class Test[T = str]:
                     def foo(self, x: T):
                         x.capitalize() # OK
                         x.<warning descr="Cannot find reference 'is_integer' in 'T'">is_integer</warning>()  # E
                 """);
  }

  // PY-55691
  public void testAttrsClassMembersProviderAttrsProperty() {
    runWithAdditionalClassEntryInSdkRoots("packages", () ->
      doTestByText(
          """
            import attrs
            
            @attrs.define
            class User:
                password: str
            
            User().__attrs_attrs__ # OK
            """)
    );
  }

  // PY-82699
  public void testTypeParameterRebind() {
    doTestByText("""
                   def outer1[T]() -> None:
                       print(<error descr="Unresolved reference 'T'">T</error>)
                       T = 1
                   
                   def outer2[T]() -> None:
                       print(T)
                   """);
  }

}
