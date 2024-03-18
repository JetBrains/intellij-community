// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.SlowOperations;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class FormClassIndex extends ScalarIndexExtension<String> {
  public static final @NonNls ID<String, Void> NAME = ID.create("com.intellij.uiDesigner.FormClassIndex");
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();

  @Override
  public @NotNull ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(GuiFormFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    @Override
    public @NotNull Map<String, Void> map(final @NotNull FileContent inputData) {
      String className = null;
      try {
        className = Utils.getBoundClassName(inputData.getContentAsText().toString());
      }
      catch (Exception e) {
        // ignore
      }
      if (className != null) {
        return Collections.singletonMap(className, null);
      }
      return Collections.emptyMap();
    }
  }

  public static List<PsiFile> findFormsBoundToClass(Project project, String className) {
    return findFormsBoundToClass(project, className, ProjectScope.getAllScope(project));
  }

  public static List<PsiFile> findFormsBoundToClass(final Project project,
                                                    final String className,
                                                    final GlobalSearchScope scope) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-648610")) {
      return ReadAction.compute(() -> {
        final Collection<VirtualFile> files;
        try {
          files = FileBasedIndex.getInstance().getContainingFiles(NAME, className,
                                                                  GlobalSearchScope.projectScope(project).intersectWith(scope));
        }
        catch (IndexNotReadyException e) {
          return Collections.emptyList();
        }
        if (files.isEmpty()) return Collections.emptyList();
        List<PsiFile> result = new ArrayList<>();
        for (VirtualFile file : files) {
          if (!file.isValid()) continue;
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (psiFile != null) {
            result.add(psiFile);
          }
        }
        return result;
      });
    }
  }

  public static List<PsiFile> findFormsBoundToClass(Project project, @NotNull PsiClass psiClass) {
    String qName = FormReferencesSearcher.getQualifiedName(psiClass);
    if (qName == null) return Collections.emptyList();
    return findFormsBoundToClass(project, qName);
  }

  public static List<PsiFile> findFormsBoundToClass(Project project, PsiClass psiClass, GlobalSearchScope scope) {
    String qName = FormReferencesSearcher.getQualifiedName(psiClass);
    if (qName == null) return Collections.emptyList();
    return findFormsBoundToClass(project, qName, scope);
  }
}
