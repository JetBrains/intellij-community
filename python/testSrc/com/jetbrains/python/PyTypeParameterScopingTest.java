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

  public void testFunctionDeclaresOwnTypeVarInReturnTypeAnnotation() {
    assertTypeParameterOwner("func", "func",
                             """
                               from typing import TypeVar

                               T = TypeVar('T')

                               def func() -> T:
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

  public void testFunctionDeclaresOwnTypeVarInReturnTypeComment() {
    assertTypeParameterOwner("func", "func",
                             """
                               from typing import TypeVar

                               T = TypeVar('T')

                               def func():
                                   # type: () -> T
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

  public void testFunctionDeclaresOwnTypeVarInReturnTypeDocstring() {
    assertTypeParameterOwner("func", "func",
                             """
                               def func():
                                   ""\"
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

  public void testFunctionDeclaresOwnTypeVarTuple() {
    assertTypeParameterOwner("func", "func",
                             """
                              from typing import TypeVarTuple
                               
                              Ts = TypeVarTuple('Ts')
                               
                              def func(xs: tuple[*Ts]) -> tuple[*Ts]:
                                  pass
                              """);
  }

  public void testMethodDeclaresOwnTypeVarTuple() {
    assertTypeParameterOwner("C.m", "C.m",
                             """
                               from typing import TypeVarTuple
                                                             
                               Ts = TypeVarTuple('Ts')
                                                            
                               class C:
                                   def m(self, xs: tuple[*Ts]) -> tuple[*Ts]:
                                       pass
                               """);
  }

  public void testMethodUsesTypeVarTupleOfItsClass() {
    assertTypeParameterOwner("C.m", "C",
                             """
                               from typing import Generic, TypeVarTuple
                                                             
                               Ts = TypeVarTuple('Ts')
                                                            
                               class C(Generic[*Ts]):
                                   def m(self, xs: tuple[*Ts]) -> tuple[*Ts]:
                                       pass
                               """);
  }

  public void testClassDeclaresItsOwnTypeVarTuple() {
    assertTypeParameterOwner("C", "C",
                             """
                               from typing import Generic, TypeVarTuple
                               
                               Ts = TypeVarTuple('Ts')
                               
                               class C(Generic[*Ts]):
                                   pass
                               """
    );
  }

  public void testFunctionUsesTypeVarTupleOfEnclosingFunction() {
    assertTypeParameterOwner("dec.g", "dec",
                             """
                               from typing import TypeVarTuple

                               Ts = TypeVarTuple('Ts')

                               def dec(x: tuple[*Ts]):
                                   def g(func) -> tuple[*Ts]:
                                       ...
                                   return g
                               """);
  }

  // PY-61883
  public void testFunctionDeclaresOwnTypeVarWithPEP695Syntax() {
    assertTypeParameterOwner("func", "func",
                             """
                               def func[T](x) -> T:
                                   pass
                               """
    );
  }

  // PY-61883
  public void testMethodUsesTypeVarOfItsClassInAnnotationWithPEP695Syntax() {
    assertTypeParameterOwner("C.m", "C",
                             """
                               class C[T]:
                                   def m(self, x: T) -> T:
                                       pass
                               """
    );
  }

  // PY-61883
  public void testMethodDeclaresOwnTypeVarWithPEP695Syntax() {
    assertTypeParameterOwner("C.m", "C.m",
                             """
                               class C[T]:
                                   def m[V](self, x: V) -> V:
                                       pass
                               """);
  }

  // PY-61883
  public void testInstanceAttributeUsesTypeVarOfItsClassWithPEP695Syntax() {
    assertTypeParameterOwner("C.attr", "C",
                             """
                               class C[T]:
                                   def __init__(self):
                                       self.attr: T = ...
                               """);
  }

  // PY-61883
  public void testClassDeclaresOwnParamSpecWithPEP695Syntax() {
    assertTypeParameterOwner("C", "C",
                             """                       
                               class C[**P]:
                                   ...
                               """);
  }

  // PY-61883
  public void testFunctionUsesTypeVarOfEnclosingFunctionWithPEP695Syntax() {
    assertTypeParameterOwner("dec.g", "dec",
                             """
                               def dec[T](x: T):
                                   def g(func) -> T:
                                       ...
                                   return g
                               """);
  }

  // PY-61883
  public void testFunctionUsesTypeVarOfEnclosingMethodClassWithPEP695Syntax() {
    assertTypeParameterOwner("C.method.f", "C",
                             """
                               class C[T]:
                                   def method(self, x: T) -> None:
                                       def f() -> T:
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
      if (type instanceof PyCallableType callableType && !(type instanceof PyClassLikeType)) {
        // For ParamSpecs, they cannot appear as a standalone type
        type = callableType.getParameters(typeEvalContext).get(0).getType(typeEvalContext);
      }
    }
    if (type instanceof PyTupleType tupleType) {
      type = tupleType.getElementType(0);
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
