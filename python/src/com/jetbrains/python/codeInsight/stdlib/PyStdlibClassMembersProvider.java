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
package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyStdlibClassMembersProvider extends PyClassMembersProviderBase {
  private Key<List<PyCustomMember>> mySocketMembersKey = Key.create("socket.members");

  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(PyClassType classType, PsiElement location, TypeEvalContext typeEvalContext) {
    PyClass clazz = classType.getPyClass();
    final String qualifiedName = clazz.getQualifiedName();
    if ("socket._socketobject".equals(qualifiedName)) {
      final PyFile socketFile = (PyFile)clazz.getContainingFile();
      List<PyCustomMember> socketMembers = socketFile.getUserData(mySocketMembersKey);
      if (socketMembers == null) {
        socketMembers = calcSocketMembers(socketFile);
        socketFile.putUserData(mySocketMembersKey, socketMembers);
      }
      return socketMembers;
    }

    if (location instanceof PyReferenceExpression) {
      final PyCallable mockPatchCallable = mockPatchCallable(classType, ((PyReferenceExpression)location).getQualifier(), typeEvalContext);
      if (mockPatchCallable != null) {
        return calcMockPatchMembers(mockPatchCallable, typeEvalContext);
      }
    }

    return Collections.emptyList();
  }

  @Override
  public PsiElement resolveMember(PyClassType clazz, String name, PsiElement location, TypeEvalContext context) {
    final PyCallable mockPatchCallable = mockPatchCallable(clazz, location, context);
    if (mockPatchCallable != null) {
      for (PyCustomMember member : calcMockPatchMembers(mockPatchCallable, context)) {
        if (name.equals(member.getName())) {
          return member.resolve(location);
        }
      }
    }

    return super.resolveMember(clazz, name, location, context);
  }

  private static List<PyCustomMember> calcSocketMembers(PyFile socketFile) {
    List<PyCustomMember> result = new ArrayList<>();
    addMethodsFromAttr(socketFile, result, "_socketmethods");
    addMethodsFromAttr(socketFile, result, "_delegate_methods");
    return result;
  }

  @Nullable
  private static PyCallable mockPatchCallable(@NotNull PyClassType classType, @Nullable PsiElement location, @NotNull TypeEvalContext context) {
    if (!PyNames.FAKE_FUNCTION.equals(classType.getClassQName())) {
      return null;
    }

    return Optional
      .ofNullable(PyUtil.as(location, PyReferenceExpression.class))
      .map(context::getType)
      .map(qualifierType -> PyUtil.as(qualifierType, PyFunctionType.class))
      .map(PyFunctionType::getCallable)
      .filter(callable -> "unittest.mock.patch".equals(QualifiedNameFinder.getQualifiedName(callable)))
      .orElse(null);
  }

  @NotNull
  private static List<PyCustomMember> calcMockPatchMembers(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (!context.maySwitchToAST(callable)) {
      return Collections.emptyList();
    }

    final List<PyCustomMember> result = new ArrayList<>();

    callable.getContainingFile().acceptChildren(
      new PyElementVisitor() {
        @Override
        public void visitPyElement(PyElement node) {
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
          final PyTargetExpression lhs = PyUtil.as(node.getLeftHandSideExpression(), PyTargetExpression.class);
          final PyExpression qualifier = lhs == null ? null : lhs.getQualifier();
          final String name = lhs == null ? null : lhs.getName();

          if (lhs != null && qualifier != null && name != null && "patch".equals(qualifier.getName())) {
            result.add(new PyCustomMember(name, lhs));
          }
        }
      }
    );

    return result;
  }

  private static void addMethodsFromAttr(PyFile socketFile, List<PyCustomMember> result, final String attrName) {
    final PyTargetExpression socketMethods = socketFile.findTopLevelAttribute(attrName);
    if (socketMethods != null) {
      final List<String> methods = PyUtil.getStringListFromTargetExpression(socketMethods);
      if (methods != null) {
        for (String name : methods) {
          result.add(new PyCustomMember(name).resolvesTo("_socket").toClass("SocketType").toFunction(name));
        }
      }
    }
  }
}
