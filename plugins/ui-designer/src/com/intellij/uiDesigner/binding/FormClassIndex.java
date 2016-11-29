/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class FormClassIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = ID.create("FormClassIndex");
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();

  @Override
  @NotNull
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  @NotNull
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.GUI_DESIGNER_FORM);
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
    @NotNull
    public Map<String, Void> map(@NotNull final FileContent inputData) {
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
    return ApplicationManager.getApplication().runReadAction(new Computable<List<PsiFile>>() {
      @Override
      public List<PsiFile> compute() {
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
        for(VirtualFile file: files) {
          if (!file.isValid()) continue;
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (psiFile != null) {
            result.add(psiFile);
          }
        }
        return result;
      }
    });
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
