package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.PsiFileStub;
import com.jetbrains.python.psi.PyFile;

import java.util.BitSet;
import java.util.List;

/**
 * @author yole
 */
public interface PyFileStub extends PsiFileStub<PyFile> {
  List<String> getDunderAll();
  BitSet getFutureFeatures();
  String getDeprecationMessage();
}
