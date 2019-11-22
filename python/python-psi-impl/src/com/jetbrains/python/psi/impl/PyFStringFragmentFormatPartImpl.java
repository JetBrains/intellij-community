// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFStringFragmentFormatPart;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyFStringFragmentFormatPartImpl extends PyElementImpl implements PyFStringFragmentFormatPart {
  public PyFStringFragmentFormatPartImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  @Override
  public List<PyFStringFragment> getFragments() {
    return findChildrenByType(PyElementTypes.FSTRING_FRAGMENT);
  }
}
