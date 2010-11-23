package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyClass;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class PyClassMembersProviderBase implements PyClassMembersProvider {
  @Override
  public Collection<PyDynamicMember> getMembers(PyClass clazz) {
    return Collections.emptyList();
  }

  @Override
  public PsiElement resolveMember(PyClass clazz, String name) {
    final Collection<PyDynamicMember> settings = getMembers(clazz);
    for (PyDynamicMember setting : settings) {
      if (setting.getName().equals(name)) {
        return setting.resolve(clazz);
      }
    }
    return null;
  }
}
