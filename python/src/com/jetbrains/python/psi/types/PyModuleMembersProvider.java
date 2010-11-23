package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class PyModuleMembersProvider {
  public static final ExtensionPointName<PyModuleMembersProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyModuleMembersProvider");

  public abstract Collection<PyDynamicMember> getMembers(PsiFile module);

  @Nullable
  public PsiElement resolveMember(PsiFile module, String name) {
    for (PyDynamicMember o : getMembers(module)) {
      if (o.getName().equals(name)) {
        return o.resolve(module);
      }
    }
    return null;
  }
}
