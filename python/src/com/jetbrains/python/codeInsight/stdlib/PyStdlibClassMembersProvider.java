// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyStdlibClassMembersProvider extends PyClassMembersProviderBase {

  @NotNull
  private static final Key<List<PyCustomMember>> SOCKET_MEMBERS_KEY = Key.create("socket.members");

  @NotNull
  public static final List<PyCustomMember> MOCK_PATCH_MEMBERS = calcMockPatchMembers();

  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(PyClassType classType, PsiElement location, @NotNull TypeEvalContext context) {
    final PyClass clazz = classType.getPyClass();
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
      final PyExpression qualifier = ((PyReferenceExpression)location).getQualifier();
      if (qualifier instanceof PyReferenceExpression && referenceToMockPatch((PyReferenceExpression)qualifier, context)) {
        return MOCK_PATCH_MEMBERS;
      }
    }

    return Collections.emptyList();
  }

  @Override
  @Nullable
  public PsiElement resolveMember(@NotNull PyClassType type,
                                  @NotNull String name,
                                  @Nullable PsiElement location,
                                  @NotNull PyResolveContext resolveContext) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    if (location instanceof PyReferenceExpression && referenceToMockPatch((PyReferenceExpression)location, context)) {
      for (PyCustomMember member : MOCK_PATCH_MEMBERS) {
        if (name.equals(member.getName())) {
          return member.resolve(location, resolveContext);
        }
      }
    }

    return super.resolveMember(type, name, location, resolveContext);
  }

  public static boolean referenceToMockPatch(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    final PyType type = context.getType(referenceExpression);
    if (type instanceof PyFunctionType) {
      return "unittest.mock.patch".equals(((PyFunctionType)type).getCallable().getQualifiedName());
    }
    return false;
  }

  private static List<PyCustomMember> calcSocketMembers(PyFile socketFile) {
    final List<PyCustomMember> result = new ArrayList<>();
    addMethodsFromAttr(socketFile, result, "_socketmethods");
    addMethodsFromAttr(socketFile, result, "_delegate_methods");
    return result;
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
