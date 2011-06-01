package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;

import java.util.BitSet;
import java.util.List;

/**
 * @author yole
 */
public class PyFileStubImpl extends PsiFileStubImpl<PyFile> implements PyFileStub {
  private final List<String> myDunderAll;
  private final BitSet myFutureFeatures; // stores IDs of features

  private static final int FUTURE_FEATURE_SET_SIZE = 32; // 32 features is ought to be enough for everybody! all bits fit into an int.

  public PyFileStubImpl(final PyFile file) {
    super(file);
    final PyFileImpl fileImpl = (PyFileImpl)file;
    myFutureFeatures = new BitSet(FUTURE_FEATURE_SET_SIZE);
    myDunderAll = fileImpl.calculateDunderAll();
    for (FutureFeature fuf : FutureFeature.ALL) {
      myFutureFeatures.set(fuf.ordinal(), fileImpl.calculateImportFromFuture(fuf));
    }
  }

  public PyFileStubImpl(List<String> dunderAll, final BitSet future_features) {
    super(null);
    myDunderAll = dunderAll;
    myFutureFeatures = future_features;
  }

  @Override
  public List<String> getDunderAll() {
    return myDunderAll;
  }

  @Override
  public BitSet getFutureFeatures() {
    return myFutureFeatures;
  }

  @Override
  public IStubFileElementType getType() {
    return PythonLanguage.getInstance().getFileElementType();
  }
}
