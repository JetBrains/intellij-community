/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.paths;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class StaticPathReferenceProvider implements PathReferenceProvider {

  private boolean myEndingSlashNotAllowed;
  private boolean myRelativePathsAllowed;

  public boolean createReferences(@NotNull final PsiElement psiElement, @NotNull final List<PsiReference> references, final boolean soft) {
    FileReferenceSet set = FileReferenceSet.createSet(psiElement, soft, myEndingSlashNotAllowed, true);
    if (set == null) {
      return false;
    }
    if (!myRelativePathsAllowed) {
      set.addCustomization(
        FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION,
        new Function<PsiFile, PsiFileSystemItem>() {
          @Nullable
          public PsiFileSystemItem fun(final PsiFile file) {
            return FileReferenceSet.getAbsoluteTopLevelDirLocation(file
            );
          }
        }
      );
    }
    Collections.addAll(references, set.getAllReferences());
    return true;
  }

  @Nullable
  public PathReference getPathReference(@NotNull final String path, @NotNull final PsiElement element) {
    final ArrayList<PsiReference> list = new ArrayList<PsiReference>(5);
    createReferences(element, list, true);
    if (list.isEmpty()) return null;

    final PsiElement target = list.get(list.size() - 1).resolve();
    if (target == null) return null;

    return new PathReference(path, PathReference.ResolveFunction.NULL_RESOLVE_FUNCTION) {
      public PsiElement resolve() {
        return target;
      }
    };

  }

  public void setEndingSlashNotAllowed(final boolean endingSlashNotAllowed) {
    myEndingSlashNotAllowed = endingSlashNotAllowed;
  }

  public void setRelativePathsAllowed(final boolean relativePathsAllowed) {
    myRelativePathsAllowed = relativePathsAllowed;
  }
}
