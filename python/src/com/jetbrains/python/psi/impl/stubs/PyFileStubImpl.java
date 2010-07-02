package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;

import java.util.List;

/**
 * @author yole
 */
public class PyFileStubImpl extends PsiFileStubImpl<PyFile> implements PyFileStub {
  private final List<String> myDunderAll;

  public PyFileStubImpl(final PyFile file) {
    super(file);
    myDunderAll = ((PyFileImpl) file).calculateDunderAll();
  }

  public PyFileStubImpl(List<String> dunderAll) {
    super(null);
    myDunderAll = dunderAll;
  }

  @Override
  public List<String> getDunderAll() {
    return myDunderAll;
  }

  @Override
  public IStubFileElementType getType() {
    return PythonLanguage.getInstance().getFileElementType();
  }
}
