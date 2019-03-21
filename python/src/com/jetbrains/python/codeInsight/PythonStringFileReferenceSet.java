// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PythonStringFileReferenceSet extends FileReferenceSet {
  private final String myRootCheckedOutsideOfproject;

  public PythonStringFileReferenceSet(String str,
                                      @NotNull PsiElement element,
                                      int startInElement,
                                      String rootCheckedOutsideOfproject) {
    super(str, element, startInElement, null, SystemInfo.isFileSystemCaseSensitive, true, null, true);
    myRootCheckedOutsideOfproject = rootCheckedOutsideOfproject;
  }

  // Name of root folder which is checked outside of project.
  @NotNull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final PsiFile file = getContainingFile();
    if (file == null) return Collections.emptyList();

    // Some absolute path.
    if (myRootCheckedOutsideOfproject != null) {
      VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(myRootCheckedOutsideOfproject);
      return Collections.singleton(file.getManager().findDirectory(root));
    }

    return getContextByFileSystemItem(file);
  }

  @Override
  protected int skipIrrelevantStart(int wsHead, CharSequence decoded) {
    int res = wsHead;
    res += super.skipIrrelevantStart(wsHead, decoded);

    int schemeLength = "file://".length();
    if (decoded.length() >= schemeLength && decoded.subSequence(res, schemeLength).equals("file://")) {
      res += schemeLength;
    }

    return res;
  }

  @NotNull
  @Override
  protected Collection<PsiFileSystemItem> getContextByFileSystemItem(@NotNull PsiFileSystemItem file) {
    // We resolve relative paths against current directory and against project root.
    List<PsiFileSystemItem> res = new ArrayList<>(getParentDirectoryContext());
    // Project root.
    VirtualFile projectBaseDir = ProjectUtil.guessProjectDir(file.getProject());
    if (projectBaseDir != null) {
      res.add(file.getManager().findDirectory(projectBaseDir));
    }

    return res;
  }

  @Override
  protected boolean isSoft() {
    return true;
  }
}
