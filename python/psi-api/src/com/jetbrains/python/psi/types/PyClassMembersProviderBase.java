package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class PyClassMembersProviderBase implements PyClassMembersProvider {
  @NotNull
  @Override
  public Collection<PyDynamicMember> getMembers(PyClassType clazz, PsiElement location) {
    return Collections.emptyList();
  }

  @Override
  public PsiElement resolveMember(PyClassType clazz, String name, PsiElement location) {
    final Collection<PyDynamicMember> members = getMembers(clazz, location);
    return resolveMemberByName(members, clazz, name);
  }

  @Nullable
  public static PsiElement resolveMemberByName(Collection<PyDynamicMember> members,
                                               PyClassType clazz,
                                               String name) {
    final PyClass pyClass = clazz.getPyClass();
    for (PyDynamicMember member : members) {
      if (member.getName().equals(name)) {
        return member.resolve(pyClass);
      }
    }
    return null;
  }
}
