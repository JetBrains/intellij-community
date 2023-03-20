// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class PyTypeParameterScopingTest extends PyTestCase {

  public void testFunctionDeclaresOwnTypeVarInAnnotation() {
    assertTypeParameterOwner("func", "func",
                             """
                               from typing import TypeVar

                               T = TypeVar('T')

                               def func(x: T) -> T:
                                   pass
                               """
    );
  }

  public void testFunctionDeclaresOwnTypeVarInTypeComment() {
    assertTypeParameterOwner("func", "func",
                             """
                               from typing import TypeVar

                               T = TypeVar('T')

                               def func(x):
                                   # type: (T) -> T
                                   pass
                               """
    );
  }

  public void testFunctionDeclaresOwnTypeVarInDocstring() {
    assertTypeParameterOwner("func", "func",
                             """
                               def func(x):
                                   ""\"
                                   :type x: T\s
                                   :rtype: T
                                   ""\"
                                   pass
                               """
    );
  }

  public void testMethodUsesTypeVarOfItsClassInAnnotation() {
    assertTypeParameterOwner("C.m", "C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               class C(Generic[T]):
                                   def m(self, x: T) -> T:
                                       pass
                               """
    );
  }

  public void testMethodUsesTypeVarOfItsClassInTypeComment() {
    assertTypeParameterOwner("C.m", "C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               class C(Generic[T]):
                                   def m(self):
                                       # type: (T) -> T
                                       pass
                               """
    );
  }

  public void testMethodUsesTypeVarOfItsClassInDocstring() {
    assertTypeParameterOwner("C.m", "C",
                             """
                               class C:
                                   def __init__(self, x):
                                       ""\"
                                       :type x: T\s
                                       :rtype: C[T]
                                       ""\"
                                       pass

                                   def m(self):
                                       ""\"
                                       :rtype: T\s
                                       ""\"
                                       pass
                               """
    );
  }

  public void testMethodDeclaresOwnTypeVarInAnnotation() {
    assertTypeParameterOwner("C.m", "C.m",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')
                               V = TypeVar('V')

                               class C(Generic[T]):
                                   def m(self, x: V) -> V:
                                       pass
                               """);
  }

  public void testMethodDeclaresOwnTypeVarInTypeComment() {
    assertTypeParameterOwner("C.m", "C.m",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')
                               V = TypeVar('V')

                               class C(Generic[T]):
                                   def m(self):
                                       # type: (V) -> V
                                       pass
                               """);
  }

  public void testMethodDeclaresOwnTypeVarInDocstring() {
    assertTypeParameterOwner("C.m", "C.m",
                             """
                               class C:
                                   def __init__(self, x):
                                       ""\"
                                       :type x: T\s
                                       :rtype: C[T]
                                       ""\"
                                       pass

                                   def m(self, x):
                                       ""\"
                                       :type x: U
                                       :rtype: U
                                       ""\"
                                       pass
                               """
    );
  }

  public void testClassAttributeUsesTypeVarOfItsClassInAnnotation() {
    assertTypeParameterOwner("C.attr", "C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               class C(Generic[T]):
                                   attr: T
                               """);
  }

  public void testClassAttributeUsesTypeVarOfItsClassInTypeComment() {
    assertTypeParameterOwner("C.attr", "C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               class C(Generic[T]):
                                   attr: T
                               """);
  }

  public void testInstanceAttributeUsesTypeVarOfItsClassInAnnotation() {
    assertTypeParameterOwner("C.attr", "C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               class C(Generic[T]):
                                   def __init__(self):
                                       self.attr: T = ...
                               """);
  }

  public void testInstanceAttributeUsesTypeVarOfItsClassAlsoUsedInConstructorInAnnotation() {
    assertTypeParameterOwner("C.attr", "C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               class C(Generic[T]):
                                   def __init__(self, p: T):
                                       self.attr: T = p
                               """);
  }

  public void testInstanceAttributeUsesOwnTypeVarOfConstructorInAnnotation() {
    assertTypeParameterOwner("C.attr", "C.__init__",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')
                               T2 = TypeVar('T2')

                               class C(Generic[T]):
                                   def __init__(self, p: T2):
                                       self.attr: T2 = p
                               """);
  }

  public void testFunctionUsesTypeVarOfEnclosingFunction() {
    assertTypeParameterOwner("dec.g", "dec",
                             """
                               from typing import TypeVar

                               T = TypeVar('T')

                               def dec(x: T):
                                   def g(func) -> T:
                                       ...
                                   return g
                               """);
  }

  public void testFunctionUsesTypeVarOfNotImmediateEnclosingFunction() {
    assertTypeParameterOwner("f.intermediate.g", "f",
                             """
                               from typing import Generic, TypeVar

                               T1 = TypeVar('T1')

                               def f(x: T1) -> None:
                                   def intermediate(x: T1) -> T1:
                                       def g(x: T1) -> T1:
                                           ...
                               """);
  }

  public void testFunctionUsesTypeVarOfEnclosingMethodClass() {
    assertTypeParameterOwner("C.method.f", "C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               class C(Generic[T]):
                                   def method(self, x: T) -> None:
                                       def f() -> T:
                                           ...
                               """);
  }

  public void testMethodCannotUseTypeVarOfEnclosingFunctionAlsoDefinedInItsClass() {
    assertTypeParameterOwner("f.C.method", "f.C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               def f(x: T):
                                   class C(Generic[T]):
                                       def method(self) -> T:
                                           ...
                               """);
  }

  public void testClassCannotUseTypeVarOfEnclosingFunction() {
    assertTypeParameterOwner("f.C", "f.C",
                             """
                               from typing import Generic, TypeVar

                               T = TypeVar('T')

                               def f(x: T):
                                   class C(Generic[T]):
                                       ...
                               """);
  }

  public void testSelfTypeScope() {
    assertTypeParameterOwner("C.m", "C",
                             """
                               from typing import Self
                               
                               class C:
                                   def m(self) -> Self:
                                       ...
                               """);
  }

  public void testFunctionDeclaresOwnParamSpec() {
    assertTypeParameterOwner("func", "func",
                             """
                               from typing import Callable, ParamSpec
                                     
                               P = ParamSpec('P')
                                     
                               def func(c: Callable[P, int]) -> Callable[P, str]:
                                   ...
                               """);
  }

  public void testMethodDeclaresOwnParamSpec() {
    assertTypeParameterOwner("C.method", "C.method",
                             """
                               from typing import Callable, ParamSpec
                                                            
                               P = ParamSpec('P')
                                     
                               class C:
                                   def method(c: Callable[P, int]) -> Callable[P, str]:
                                       ...
                               """);
  }

  public void testMethodUsesParamSpecOfItsClass() {
    assertTypeParameterOwner("C.method", "C",
                             """
                               from typing import Callable, Generic, ParamSpec
                                                            
                               P = ParamSpec('P')
                                                            
                               class C(Generic[P]):
                                   def method(c: Callable[P, int]) -> Callable[P, str]:
                                       ...
                               """);
  }

  public void testClassDeclaresOwnParamSpec() {
    assertTypeParameterOwner("C", "C",
                             """
                               from typing import Generic, ParamSpec
                                                            
                               P = ParamSpec('P')
                                                            
                               class C(Generic[P]):
                                   ...
                               """);
  }

  private void assertTypeParameterOwner(@NotNull String elementQName, @NotNull String scopeOwnerQName, @NotNull String text) {
      VirtualFile virtualFile;
      try {
        virtualFile = myFixture.getTempDirFixture().createFile("a.py", text);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyFile pyFile = (PyFile)PsiManager.getInstance(myFixture.getProject()).findFile(virtualFile);

      QualifiedName elementQualifiedName = QualifiedName.fromDottedString(elementQName);
      PsiElement resolvedElement = resolveQualifiedName(pyFile, elementQualifiedName, typeEvalContext);
      assertNotNull(elementQName, resolvedElement);
      PyTypedElement typedElement = assertInstanceOf(resolvedElement, PyTypedElement.class);
      PyType type = typeEvalContext.getType(typedElement);
      if (type instanceof PyClassType classType) {
        PyCollectionType genericClassType = PyTypeChecker.findGenericDefinitionType(classType.getPyClass(), typeEvalContext);
        assertNotNull(genericClassType);
        type = genericClassType.getElementTypes().get(0);
      }
      else if (type instanceof PyFunctionType functionType) {
        type = functionType.getReturnType(typeEvalContext);
        if (type instanceof PyCallableType callableType) {
          // For ParamSpecs, they cannot appear as a standalone type
          type = callableType.getParameters(typeEvalContext).get(0).getType(typeEvalContext);
        }
      }
      PyTypeParameterType typeVar = assertInstanceOf(type, PyTypeParameterType.class);
      QualifiedName ownerQualifiedName = QualifiedName.fromDottedString(scopeOwnerQName);
      PsiElement owner = resolveQualifiedName(pyFile, ownerQualifiedName, typeEvalContext);
      assertNotNull(scopeOwnerQName, owner);
      assertEquals(owner, typeVar.getScopeOwner());

      assertNotParsed(pyFile);
  }

  private static @Nullable PsiElement resolveQualifiedName(@NotNull PyFile pyFile,
                                                           @NotNull QualifiedName elementQName,
                                                           @NotNull TypeEvalContext typeEvalContext) {
    List<PsiElement> resolvedByQualifiedName = PyResolveUtil.resolveQualifiedNameInScope(elementQName, pyFile, typeEvalContext);
    if (!resolvedByQualifiedName.isEmpty()) return assertOneElement(resolvedByQualifiedName);
    return findInStubTreeByScopePath(pyFile, elementQName);
  }

  private static @Nullable PsiElement findInStubTreeByScopePath(@NotNull PyFile file, @NotNull QualifiedName scopePath) {
    PsiElement topLevelAttr = file.findExportedName(scopePath.getFirstComponent());
    return StreamEx.of(scopePath.removeHead(1).getComponents()).foldLeft(topLevelAttr, (element, name) -> {
      if (element == null) return null;
      List<PsiNamedElement> namedStubChildren = PsiTreeUtil.getStubChildrenOfTypeAsList(element, PsiNamedElement.class);
      return ContainerUtil.find(namedStubChildren, child -> child.getName().equals(name));
    });
  }
}
