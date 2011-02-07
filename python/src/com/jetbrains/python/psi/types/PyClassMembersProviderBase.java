package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class PyClassMembersProviderBase implements PyClassMembersProvider {
  @Override
  public Collection<PyDynamicMember> getMembers(PyClassType clazz) {
    return Collections.emptyList();
  }

  @Override
  public PsiElement resolveMember(PyClassType clazz, String name) {
    final Collection<PyDynamicMember> members = getMembers(clazz);
    return resolveMemberByName(members, clazz, name);
  }

  @Nullable
  public static PsiElement resolveMemberByName(Collection<PyDynamicMember> members,
                                               PyClassType clazz,
                                               String name) {
    for (PyDynamicMember member : members) {
      if (member.getName().equals(name)) {
        return member.resolve(clazz.getPyClass());
      }
    }
    return null;
  }
}
