package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public interface PyClassMembersProvider {
  ExtensionPointName<PyClassMembersProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyClassMembersProvider");

  Collection<PyDynamicMember> getMembers(PyClassType clazz);
  @Nullable
  PsiElement resolveMember(PyClassType clazz, String name);
}
