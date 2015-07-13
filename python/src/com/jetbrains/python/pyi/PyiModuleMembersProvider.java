/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.pyi;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.NameDefiner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyiModuleMembersProvider extends PyModuleMembersProvider {
  @Nullable
  @Override
  public PsiElement resolveMember(PyFile module, String name) {
    final PsiElement pythonStub = PyiUtil.getPythonStub(module);
    if (pythonStub instanceof NameDefiner) {
      return ((NameDefiner)pythonStub).getElementNamed(name);
    }
    return null;
  }

  @Override
  public Collection<PyCustomMember> getMembers(PyFile module, PointInImport point) {
    final PsiElement pythonStub = PyiUtil.getPythonStub(module);
    if (pythonStub instanceof NameDefiner) {
      final List<PyCustomMember> results = new ArrayList<PyCustomMember>();
      for (PyElement element : ((NameDefiner)pythonStub).iterateNames()) {
        final String name = element.getName();
        if (name != null) {
          results.add(new PyCustomMember(name, element));
        }
      }
      return results;
    }
    return Collections.emptyList();
  }

  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName) {
    return null;
  }
}
