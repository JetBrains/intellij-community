// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.numpy.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PyPsiPath;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

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

  private static final String DTYPE = "numpy.core.multiarray.dtype";

  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName) {
    // This method will be removed in 2018.2
    return getMembersByQName(module, qName, TypeEvalContext.codeInsightFallback(module.getProject()));
  }

  @Override
  @NotNull
  protected Collection<PyCustomMember> getMembersByQName(@NotNull PyFile module, @NotNull String qName, @NotNull TypeEvalContext context) {
    if ("numpy".equals(qName)) {
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
      final PsiElement clazz = new PyPsiPath.ToClassQName(DTYPE).resolve(module, resolveContext);
      if (clazz != null) {
        final List<PyCustomMember> members = new ArrayList<>();
        for (String type : NUMERIC_TYPES) {
          members.add(new PyCustomMember(type, clazz, DTYPE));
        }
        for (String type : PYTHON_TYPES) {
          members.add(new PyCustomMember(type, clazz, DTYPE));
        }
        addTestingModule(module, members);
        return members;
      }
    }
    return Collections.emptyList();
  }

  private static void addTestingModule(PyFile module, List<PyCustomMember> members) {
    final PyQualifiedNameResolveContext context = PyResolveImportUtil.fromFoothold(module).copyWithPlainDirectories();
    final PsiElement resolved = PyResolveImportUtil.resolveQualifiedName(QualifiedName.fromDottedString("numpy.testing"), context)
      .stream().findFirst().orElse(null);
    final PsiElement testingModule = PyUtil.turnDirIntoInit(resolved);
    members.add(new PyCustomMember("testing", testingModule));
  }
}
