package com.jetbrains.python.psi;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Resolves absolute paths from FS root, not content roots
 *
 * @author traff
 */
public class RootFileReferenceSet extends FileReferenceSet {
  public RootFileReferenceSet(String str,
                              PsiElement element,
                              int startInElement,
                              PsiReferenceProvider provider,
                              boolean caseSensitive,
                              boolean endingSlashNotAllowed, @Nullable FileType[] suitableFileTypes) {
    super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes);
  }

  public RootFileReferenceSet(String s, PsiElement element, int offset, PsiReferenceProvider provider, boolean sensitive) {
    super(s, element, offset, provider, sensitive);
  }

  public boolean isAbsolutePathReference() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      return FileUtil.isAbsolute(getPathString());
    }
    else {
      return super.isAbsolutePathReference();
    }
  }

  @NotNull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final PsiFile file = getContainingFile();
    if (file != null) {

      if (isAbsolutePathReference()) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          VirtualFile root = LocalFileSystem.getInstance().getRoot();
          PsiDirectory directory = file.getManager().findDirectory(root);
          if (directory != null) {
            return Lists.<PsiFileSystemItem>newArrayList(directory);
          }
        }
        else {
          return super.computeDefaultContexts();
        }
      }
      else {
        return super.computeDefaultContexts();
      }
    }

    return Collections.emptyList();
  }
}
