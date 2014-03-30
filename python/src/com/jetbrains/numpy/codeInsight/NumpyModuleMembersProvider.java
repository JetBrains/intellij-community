/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyFile;
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
    "complex32", "complex64", "complex128", "complex160", "complex192", "complex256", "complex512"
  };

  @Override
  protected Collection<PyDynamicMember> getMembersByQName(PyFile module, String qName) {
    if ("numpy".equals(qName)) {
      final List<PyDynamicMember> members = new ArrayList<PyDynamicMember>();
      for (String type : NUMERIC_TYPES) {
        members.add(new PyDynamicMember(type, "numpy.core.multiarray.dtype", false));
      }
      return members;
    }
    return Collections.emptyList();
  }
}
