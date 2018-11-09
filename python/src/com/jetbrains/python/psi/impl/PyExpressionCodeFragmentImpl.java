// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * clone of JSExpressionCodeFragment
 */
public class PyExpressionCodeFragmentImpl extends PyFileImpl implements PyExpressionCodeFragment {
  private PsiElement myContext;
  private boolean myPhysical;
  private FileViewProvider myViewProvider;

  public PyExpressionCodeFragmentImpl(Project project, @NonNls String name, CharSequence text, boolean isPhysical) {
    super(PsiManagerEx.getInstanceEx(project).getFileManager().createFileViewProvider(
            new LightVirtualFile(name, FileTypeManager.getInstance().getFileTypeByFileName(name), text), isPhysical)
    );
    myPhysical = isPhysical;
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
  }

  @Override
  protected PyExpressionCodeFragmentImpl clone() {
    final PyExpressionCodeFragmentImpl clone = (PyExpressionCodeFragmentImpl)cloneImpl((FileElement)calcTreeElement().clone());
    clone.myPhysical = false;
    clone.myOriginalFile = this;
    FileManager fileManager = ((PsiManagerEx)getManager()).getFileManager();
    SingleRootFileViewProvider cloneViewProvider = (SingleRootFileViewProvider)fileManager.createFileViewProvider(new LightVirtualFile(getName(), getLanguage(), getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }

  @Override
  public PsiElement getContext() {
    return myContext != null && myContext.isValid() ? myContext : super.getContext();
  }

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }

  @Override
  public boolean isPhysical() {
    return myPhysical;
  }

  public void setContext(PsiElement context) {
    myContext = context;
  }

}
