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
import com.intellij.util.containers.ContainerUtil;
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

  @NotNull
  private static final Key<List<PyCustomMember>> SOCKET_MEMBERS_KEY = Key.create("socket.members");

  @NotNull
  private static final List<PyCustomMember> MOCK_PATCH_MEMBERS = calcMockPatchMembers();

  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(PyClassType classType, PsiElement location, TypeEvalContext typeEvalContext) {
    PyClass clazz = classType.getPyClass();
    final String qualifiedName = clazz.getQualifiedName();
    if ("socket._socketobject".equals(qualifiedName)) {
      final PyFile socketFile = (PyFile)clazz.getContainingFile();
      List<PyCustomMember> socketMembers = socketFile.getUserData(SOCKET_MEMBERS_KEY);
      if (socketMembers == null) {
        socketMembers = calcSocketMembers(socketFile);
        socketFile.putUserData(SOCKET_MEMBERS_KEY, socketMembers);
      }
      return socketMembers;
    }

    if (location instanceof PyReferenceExpression) {
      final PyCallable mockPatchCallable = mockPatchCallable(classType, ((PyReferenceExpression)location).getQualifier(), typeEvalContext);
      if (mockPatchCallable != null) {
        return MOCK_PATCH_MEMBERS;
      }
    }

    return Collections.emptyList();
  }

  @Override
  public PsiElement resolveMember(PyClassType clazz, String name, @Nullable PsiElement location, TypeEvalContext context) {
    final PyCallable mockPatchCallable = mockPatchCallable(clazz, location, context);
    if (mockPatchCallable != null && location!= null) {
      for (PyCustomMember member : MOCK_PATCH_MEMBERS) {
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
    if (!PyNames.TYPES_FUNCTION_TYPE.equals(classType.getClassQName())) {
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
  private static List<PyCustomMember> calcMockPatchMembers() {
    final String[] members = new String[]{"object", "dict", "multiple", "stopall", "TEST_PREFIX"};
    final String moduleQName = "unittest.mock";

    return ContainerUtil.map(members, member -> new PyCustomMember(member).resolvesTo(moduleQName).toAssignment("patch." + member));
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
