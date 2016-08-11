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
package com.jetbrains.numpy.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Provides 'numpy' module dynamic members for numeric types.
 *
 * @author avereshchagin
 * @author vlan
 */
public class NumpyModuleMembersProvider extends PyModuleMembersProvider {
  private static final String[] NUMERIC_TYPES = {
    "int8", "int16", "int32", "int64", "int128",
    "uint8", "uint16", "uint32", "uint64", "uint128",
    "float16", "float32", "float64", "float80", "float96", "float128", "float256",
    "complex32", "complex64", "complex128", "complex160", "complex192", "complex256", "complex512", "double"
  };
  private static final String[] PYTHON_TYPES = {
    "int_", "bool_", "float_", "cfloat", "string_", "str_",
    "unicode_", "object_", "complex_", "bytes_", "byte", "ubyte", "void",
    "short", "ushort", "intc", "uintc", "intp", "uintp", "uint",
    "longlong", "ulonglong", "single", "csingle",
    "longfloat", "clongfloat"};

  private static String DTYPE = "numpy.core.multiarray.dtype";

  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName) {
    if ("numpy".equals(qName)) {
      final List<PyCustomMember> members = new ArrayList<>();
      for (String type : NUMERIC_TYPES) {
        members.add(new PyCustomMember(type, DTYPE, false));
      }
      for (String type : PYTHON_TYPES) {
        members.add(new PyCustomMember(type, DTYPE, false));
      }
      addTestingModule(module, members);
      return members;
    }
    return Collections.emptyList();
  }

  private static void addTestingModule(PyFile module, List<PyCustomMember> members) {
    PyPsiFacade psiFacade = PyPsiFacade.getInstance(module.getProject());
    final QualifiedNameResolver resolver =
      psiFacade.qualifiedNameResolver(QualifiedName.fromDottedString("numpy.testing")).withPlainDirectories().fromElement(module);
    PsiElement testingModule = PyUtil.turnDirIntoInit(resolver.firstResult());
    members.add(new PyCustomMember("testing", testingModule));
  }
}
