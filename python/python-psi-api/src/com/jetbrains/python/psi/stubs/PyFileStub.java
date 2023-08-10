// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.PsiFileStub;
import com.jetbrains.python.psi.PyFile;

import java.util.BitSet;
import java.util.List;


public interface PyFileStub extends PsiFileStub<PyFile> {
  List<String> getDunderAll();
  BitSet getFutureFeatures();
  String getDeprecationMessage();
}
