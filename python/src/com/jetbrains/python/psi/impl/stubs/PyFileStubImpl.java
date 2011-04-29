package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;

import java.util.List;

import static com.jetbrains.python.psi.FutureFeature.ABSOLUTE_IMPORT;

/**
 * @author yole
 */
public class PyFileStubImpl extends PsiFileStubImpl<PyFile> implements PyFileStub {
  private final List<String> myDunderAll;
  private final boolean myAbsoluteImportEnabled;

  public PyFileStubImpl(final PyFile file) {
    super(file);
    final PyFileImpl fileImpl = (PyFileImpl)file;
    myDunderAll = fileImpl.calculateDunderAll();
    myAbsoluteImportEnabled = fileImpl.calculateImportFromFuture(ABSOLUTE_IMPORT);
  }

  public PyFileStubImpl(List<String> dunderAll, final boolean absoluteImportEnabled) {
    super(null);
    myDunderAll = dunderAll;
    myAbsoluteImportEnabled = absoluteImportEnabled;
  }

  @Override
  public List<String> getDunderAll() {
    return myDunderAll;
  }

  @Override
  public boolean isAbsoluteImportEnabled() {
    return myAbsoluteImportEnabled;
  }

  @Override
  public IStubFileElementType getType() {
    return PythonLanguage.getInstance().getFileElementType();
  }
}
