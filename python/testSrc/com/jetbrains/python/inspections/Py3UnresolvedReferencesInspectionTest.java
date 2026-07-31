// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.idea.TestFor;
import com.intellij.lang.FileASTNode;
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

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Subsystems.Inspections
@Layers.Functional
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
    doMultiFileTest(getTestName(false), filename, sourceRoots);
  }

  protected void doMultiFileTest(@NotNull String testDirectory, @NotNull String filename, @NotNull List<String> sourceRoots) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + testDirectory, "");
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


  // PY-24834
  public void testStrictUnionMemberAttributeAccess() {
    doTest();
  }

  // PY-24834
  public void testStrictUnionMemberOperatorAccess() {
    doTest();
  }

  // PY-24834
  public void testStrictUnionMemberExtendingAny() {
    doTest();
  }

  // PY-50642
  public void testTypeChecking() {
    doTestByText("""
                   import typing
                   
                   if not typing.TYPE_CHECKING:
                       x: str = 'ab'
                   
                   class A:
                       if not typing.TYPE_CHECKING:
                           foo: int = -1
                       ...
                   
                   if not typing.TYPE_CHECKING:
                       _ = x
                       _ = A.foo
                   """);
  }

  // PY-83529
  public void testPackageAttributeInPresenceOfBinarySkeleton() {
    runWithAdditionalClassEntryInSdkRoots(getTestDirectoryPath() + "/site-packages", () -> {
      runWithAdditionalClassEntryInSdkRoots(getTestDirectoryPath() + "/python_stubs", () -> {
        final PsiFile currentFile = myFixture.configureByFile(getTestDirectoryPath() + "/main.py");
        configureInspection();
        assertSdkRootsNotParsed(currentFile);
      });
    });
  }

  // PY-55589
  public void testPartialStubPackageUser() {
    doMultiFileTest("a.py");
  }

  // PY-55589
  public void testPartialStubPackageUserNested() {
    doMultiFileTest("a/b.py");
  }

  // PY-55589
  public void testPartialStubPackageUserNestedWithSiblingStub() {
    doMultiFileTest("a/b.py");
  }

  // PY-55589
  public void testPartialStubPackageUserNestedWithSiblingStubSourceRoot() {
    doMultiFileTest("PartialStubPackageUserNestedWithSiblingStub", "a/b.py", Collections.singletonList("mypackage-stubs"));
  }

  // PY-85880
  public void testLiteralUnionInTuple() {
    doTestByText("""
                   from typing import Literal
                   
                   
                   def f(e: Literal[1, 2]):
                       _ = e in ()
                   """);
  }

  // PY-85880
  public void testLiteralInUnionTupleNone() {
    doTestByText("""
                   from typing import Literal
                   
                   
                   def f(e: Literal[1, 2]):
                       a: tuple | None = None
                       _ = e <weak_warning descr="Member 'None' of 'tuple[Unknown, ...] | None' does not have attribute '__contains__'">in</weak_warning> a
                   """);
  }

  // PY-85941
  public void testSuperCallResultAttributes() {
    doTestByText("""
                   from abc import ABC
                   
                   class A:
                       def do_smth(self):
                           print("Something from", self)
                   
                   class B(A, ABC):
                       def do_smth(self):
                           print("Something more from", self)
                           super().do_smth()
                           super().<warning descr="Cannot find reference 'non_existing' in 'UnsafeUnion[A, ABC]'">non_existing</warning>()
                   """);
  }

  // PY-76922
  public void testIntersectionMemberAttributeAccess() {
    doTest();
  }

  // PY-86608
  public void testFromImportComprehensionVariableLeak() {
    doMultiFileTest();
  }

  // PY-86608
  public void testFromImportComprehensionVariableLeakUnstubbed() {
    doUnstubbedComprehensionVariableLeakTest("FromImportComprehensionVariableLeak");
  }

  @TestFor(issues = "PY-88569")
  public void testFromImportWalrusComprehensionVariableLeak() {
    doMultiFileTest();
  }

  @TestFor(issues = "PY-88569")
  public void testFromImportWalrusComprehensionVariableLeakUnstubbed() {
    doUnstubbedComprehensionVariableLeakTest("FromImportWalrusComprehensionVariableLeak");
  }

  private void doUnstubbedComprehensionVariableLeakTest(@NotNull String testDirName) {
    String testDir = getTestCaseDirectory() + testDirName;
    myFixture.copyDirectoryToProject(testDir, "");
    PsiFile cPy = myFixture.configureFromTempProjectFile("c.py");

    FileASTNode cPyNode = cPy.getNode();
    assertTrue(cPyNode.isParsed());

    PsiFile aPy = myFixture.configureFromTempProjectFile("a.py");

    configureInspection();

    assertSdkRootsNotParsed(aPy);
    Reference.reachabilityFence(cPyNode);
  }

  // PY-87343
  public void testNewTypeUnion() {
    doTestByText("""
                   from typing import NewType
                   
                   MyId = NewType("MyId", int)
                   
                   val: MyId | None = None
                   """);
  }

  // PY-89245
  public void testFlakyLoop() {
    doTestByText("""
                   class ListNode:
                       def __init__(self, val=0, next=None):
                           self.val = val
                           self.next = next
                   
                   
                   def find_by_value(node: ListNode | None, val: int) -> ListNode | None:
                       while node is not None and node.val != val:
                           node = node.next
                       return node
                   """);
  }

  // PY-40883
  public void testStrictClassAttributes() {
    doTest();
  }

  // PY-40883
  public void testStrictClassAttributesOff() {
    final PyUnresolvedReferencesInspection inspection = new PyUnresolvedReferencesInspection();
    inspection.strictClassAttributes = false;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestCaseDirectory() + getTestName(true) + ".py");
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }

  @TestFor(issues="PY-87799")
  public void testStrictInstanceAttributes() {
    doTest();
  }

  @TestFor(issues="PY-87799")
  public void testStrictInstanceAttributesOff() {
    final PyUnresolvedReferencesInspection inspection = new PyUnresolvedReferencesInspection();
    inspection.strictInstanceAttributes = false;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestCaseDirectory() + getTestName(true) + ".py");
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }

  @TestFor(issues="PY-82245")
  public void testStringInAnnotated() {
    doTestByText(
      """
        from typing import Annotated
        
        type A = Annotated[str, print(end="foo")]
        """
    );
  }

  @TestFor(issues = "PY-80622")
  public void testAugAssignmentRaddDefinedButIaddMissingOnTarget() {
    doTestByText("""
                   class A: pass
                   class B:
                       def __radd__(self, other: A) -> str: ...
                   
                   a = A()
                   a += B()  # ok
                   
                   b = B()
                   b <warning descr="Class 'B' does not define '__iadd__', so the '+=' operator cannot be used on its instances">+=</warning> A()
                   """);
  }

  @TestFor(issues = "PY-80622")
  public void testAugAssignmentIaddNotDefinedOnClass(){
    doTestByText("""
                   class A: pass
                   
                   a = A()
                   a <warning descr="Class 'A' does not define '__iadd__', so the '+=' operator cannot be used on its instances">+=</warning> a
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberAugAssignInplaceOperatorPresent() {
    doTestByText("""
                   class WithIadd:
                       def __iadd__(self, other): pass

                   class WithAdd:
                       def __add__(self, other): pass

                   class Rhs:
                       pass
  
                   def f(x: WithIadd | WithAdd):
                       x += Rhs()  # no error: WithIadd has __iadd__, WithAdd has __add__
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberAugAssignFallbackToNormalOperator() {
    doTestByText("""
                   class WithAdd:
                       def __add__(self, other): pass

                   class WithIaddAndAdd:
                       def __iadd__(self, other): pass
                       def __add__(self, other): pass

                   class Rhs:
                       pass
  
                   def f(x: WithAdd | WithIaddAndAdd):
                       x += Rhs()  # no error: both satisfy __add__ or __iadd__
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberAugAssignReflectedOperatorOnRhsConsidered() {
    doTestByText("""
                   class Empty:
                       pass

                   class WithRadd:
                       def __radd__(self, other): pass

                   def f(x: Empty | None, y: WithRadd):
                       x += y
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberAugAssignNoFalsePositiveForFloatIntUnion() {
    doTestByText("""
                   def f(foo: float | int):
                       foo += 1
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberAugAssignNoFallback() {
    doTestByText("""
                 class Empty:
                     pass

                 class Rhs:
                     pass

                 def f(x: Empty | None):
                     x <weak_warning descr="Member 'Empty' of 'Empty | None' does not have attribute '__iadd__'">+=</weak_warning> Rhs()
                 """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberAugAssignRhsUnionPartialRadd() {
    doTestByText("""
                   class Empty:
                       pass

                   class WithRadd:
                       def __radd__(self, other): pass

                   def f(x: Empty | None, y: WithRadd | None):
                       x <weak_warning descr="Member 'Empty' of 'Empty | None' does not have attribute '__iadd__'">+=</weak_warning> y
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberAugAssignRhsUnionAllRadd() {
    doTestByText("""
                   class Empty:
                       pass

                   class WithRadd:
                       def __radd__(self, other): pass

                   class WithRadd2:
                       def __radd__(self, other): pass

                   def f(x: Empty | None, y: WithRadd | WithRadd2):
                       x += y
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorLhsMissingAddRhsPartialRadd() {
    doTestByText("""
                   class Empty:
                       pass
                   
                   class WithRadd:
                       def __radd__(self, other): pass
                   
                   def f(x: Empty | None, y: WithRadd | None):
                       _ = x <weak_warning descr="Member 'None' of 'WithRadd | None' does not have attribute '__radd__'">+</weak_warning> y
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorRhsUnionAllRadd() {
    doTestByText("""
                   class Empty:
                       pass

                   class WithRadd:
                       def __radd__(self, other): pass

                   class WithRadd2:
                       def __radd__(self, other): pass

                   def f(x: Empty | None, y: WithRadd | WithRadd2):
                       _ = x + y
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorPresent() {
    doTestByText("""
                  class WithAdd:
                      def __add__(self, other): pass

                  class WithAdd2:
                      def __add__(self, other): pass

                  class Rhs:
                      pass

                  def f(x: WithAdd | WithAdd2):
                     _ = x + Rhs()  # no error: both members have __add__
                  """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorReflectedOnRhsConsidered() {
    doTestByText("""
                   class Empty:
                       pass

                   class WithRadd:
                       def __radd__(self, other): pass

                   def f(x: Empty | None, y: WithRadd):
                       _ = x + y  # no error: WithRadd.__radd__ covers the fallback
                   """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorLhsMissingButRhsHasRadd() {
    doTestByText("""
                  class Empty:
                    pass

                  class RhsWithRadd:
                    def __radd__(self, other): pass

                  def f(x: Empty | None):
                    _ = x + RhsWithRadd()  # no strict-union warning: RHS provides __radd__
                  """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorOneMemberMissingNoRadd() {
    doTestByText("""
                  class WithAdd:
                      def __add__(self, other): pass

                  class NoRadd:
                      pass

                  def f(x: WithAdd | None):
                      _ = x <weak_warning descr="Member 'None' of 'WithAdd | None' does not have attribute '__add__'">+</weak_warning> NoRadd()
                  """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorNoFallback() {
    doTestByText("""
                  class Empty:
                      pass

                  class Rhs:
                      pass

                  def f(x: Empty | None):
                      _ = x <weak_warning descr="Member 'Empty' of 'Empty | None' does not have attribute '__add__'">+</weak_warning> Rhs()
                  """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorUnsafeUnionSomeMembersMissingAddWithAny() {
    doTestByText("""
                  from typing import Any
  
                  class WithAdd:
                      def __add__(self, other): ...
  
                  class NoAdd:
                      pass
  
                  class NoRaddRhs:
                      pass
  
                  def f(x: WithAdd | NoAdd | Any):
                      _ = x <weak_warning descr="Member 'NoAdd' of 'WithAdd | NoAdd | Any' does not have attribute '__add__'">+</weak_warning> NoRaddRhs()
                  """);
  }

  @TestFor(issues = "PY-89978")
  public void testStrictUnionMemberBinaryOperatorRhsUnsafeUnionAllRaddWithAny() {
    doTestByText("""
                  from typing import Any
    
                  class Empty:
                      pass
    
                  class WithRadd:
                      def __radd__(self, other): ...
    
                  class WithRadd2:
                      def __radd__(self, other): ...
    
                  def f(x: Empty | None, y: WithRadd | WithRadd2 | Any):
                      _ = x + y  # all non-Any RHS members define '__radd__'
                  """);
  }

  @TestFor(issues = "PY-89798")
  public void testStrictUnionAugAssignmentOnMultipleLocalVariablesNoError() {
    doTestByText("""
                  def foo() -> None:
                    left, right = 0, 42

                    while left < right:
                        left += 1
                        #  no error:  ^^ Member 'Literal[0]' of 'Literal[0] | int' does not have attribute '__iadd__'
                        right -= 1
                        #  no error:   ^^ Member 'Literal[42]' of 'Literal[42] | int' does not have attribute '__isub__'
                  """);
  }

  @TestFor(issues = "PY-90475")
  public void testStrictUnionAugAssignmentOnLocalVariableNoError() {
    doTestByText("""
                   def bar(a: int, b: int):
                       foo = 2
      
                       if a > 0:
                           foo += 1
      
                       if b > 0:
                           foo -= 1
                   """);
  }
}
