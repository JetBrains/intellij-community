package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyUserSkeletonsModuleMembersProvider extends PyModuleMembersProvider {
  @Nullable
  @Override
  public PsiElement resolveMember(PyFile module, String name) {
    final PyFile moduleSkeleton = PyUserSkeletonsUtil.getUserSkeleton(module);
    if (moduleSkeleton != null) {
      return moduleSkeleton.getElementNamed(name);
    }
    return null;
  }

  @Override
  protected Collection<PyDynamicMember> getMembersByQName(PyFile module, String qName) {
   final PyFile moduleSkeleton = PyUserSkeletonsUtil.getUserSkeletonForModuleQName(qName, module);
    if (moduleSkeleton != null) {
      final List<PyDynamicMember> results = new ArrayList<PyDynamicMember>();
      for (PyElement element : moduleSkeleton.iterateNames()) {
        if (element instanceof PsiFileSystemItem) {
          continue;
        }
        final String name = element.getName();
        if (name != null) {
          results.add(new PyDynamicMember(name, element));
        }
      }
      return results;
    }
    return Collections.emptyList();
  }
}
