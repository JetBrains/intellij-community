/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PointInImport;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public abstract class PyModuleMembersProvider {
  public static final ExtensionPointName<PyModuleMembersProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyModuleMembersProvider");

  public Collection<PyCustomMember> getMembers(PyFile module, PointInImport point) {
    final VirtualFile vFile = module.getVirtualFile();
    if (vFile != null) {
      final String qName = PyPsiFacade.getInstance(module.getProject()).findShortestImportableName(vFile, module);
      if (qName != null) {
        return getMembersByQName(module, qName);
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public PsiElement resolveMember(PyFile module, String name) {
    for (PyCustomMember o : getMembers(module, PointInImport.NONE)) {
      if (o.getName().equals(name)) {
        return o.resolve(module);
      }
    }
    return null;
  }

  protected abstract Collection<PyCustomMember> getMembersByQName(PyFile module, String qName);
}
