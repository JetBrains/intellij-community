// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PySliceItemImpl extends PyElementImpl implements PySliceItem, PyInstantTypeProvider {
  public PySliceItemImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    final PyClassType sliceType = PyBuiltinCache.getInstance(this).getSliceType();
    return sliceType != null ? sliceType : PyAnyType.getUnknown();
  }
}
