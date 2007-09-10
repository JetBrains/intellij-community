/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.util.containers.ClassMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Collection;

/**
 * @author peter
 */
public class FileReferenceHelperRegistrar {
  private static final ClassMap<FileReferenceHelper> ourHelpersMap = new ClassMap<FileReferenceHelper>();
  private static final LinkedList<FileReferenceHelper> ourHelpers = new LinkedList<FileReferenceHelper>();

  static {
    final PsiFileReferenceHelper helper = new PsiFileReferenceHelper();
    registerHelper(helper);
    ourHelpersMap.put(PsiFile.class, helper);
  }

  public static void registerHelper(FileReferenceHelper helper) {
    ourHelpers.addFirst(helper);
    ourHelpersMap.put(helper.getDirectoryClass(), helper);
  }

  public static List<FileReferenceHelper> getHelpers() {
    return ourHelpers;
  }

  @NotNull
  public static <T extends PsiFileSystemItem> FileReferenceHelper<T> getNotNullHelper(@NotNull T psiFileSystemItem) {
    final FileReferenceHelper<T> helper = getHelper(psiFileSystemItem);
    return helper == null ? new NullFileReferenceHelper<T>() : helper;
  }

  @Nullable
  public static <T extends PsiFileSystemItem> FileReferenceHelper<T> getHelper(@NotNull final T psiFileSystemItem) {
    final VirtualFile file = psiFileSystemItem.getVirtualFile();
    if (file == null) return null;
    final Project project = psiFileSystemItem.getProject();
    return (FileReferenceHelper<T>)ContainerUtil.find(getHelpers(), new Condition<FileReferenceHelper>() {
      public boolean value(final FileReferenceHelper fileReferenceHelper) {
        return fileReferenceHelper.getPsiFileSystemItem(project, file) != null;
      }
    });
  }

  public static boolean areElementsEquivalent(@NotNull final PsiFileSystemItem element1, @NotNull final PsiFileSystemItem element2) {
    return element2.getManager().areElementsEquivalent(normalizeItem(element1), normalizeItem(element2));
  }

  @Nullable
  public static PsiFileSystemItem normalizeItem(@NotNull final PsiFileSystemItem item) {
    final VirtualFile file = item.getVirtualFile();
    return file == null ? null : getNotNullHelper(item).getPsiFileSystemItem(item.getProject(), file);
  }

  private static class NullFileReferenceHelper<T extends PsiFileSystemItem> implements FileReferenceHelper<T> {
    @NotNull
    public Class<T> getDirectoryClass() {
      throw new UnsupportedOperationException("Method getDirectoryClass is not yet implemented in " + getClass().getName());
    }

    @NotNull
    public String getDirectoryTypeName() {
      throw new UnsupportedOperationException("Method getDirectoryTypeName is not yet implemented in " + getClass().getName());
    }

    @NotNull
    public String trimUrl(@NotNull final String url) {
      return url;
    }

    public PsiReference createDynamicReference(final PsiElement element, final String str) {
      return null;
    }

    public List<? extends LocalQuickFix> registerFixes(final HighlightInfo info, final FileReference reference) {
      return Collections.emptyList();
    }

    public PsiFileSystemItem getPsiFileSystemItem(final Project project, @NotNull final VirtualFile file) {
      return null;
    }

    public PsiFileSystemItem findRoot(final Project project, @NotNull final VirtualFile file) {
      return null;
    }

    @NotNull
    public Collection<PsiFileSystemItem> getRoots(@NotNull final Module module) {
      return Collections.emptyList();
    }
  }
}
