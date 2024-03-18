// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION;

public final class PyModuleNameIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> NAME = ID.create("Py.module.name");

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @NotNull
      @Override
      public Map<String, Void> map(@NotNull FileContent inputData) {
        final VirtualFile file = inputData.getFile();
        final String name = file.getName();
        if (PyNames.INIT_DOT_PY.equals(name)) {
          final VirtualFile parent = file.getParent();
          if (parent != null && parent.isDirectory()) {
            return Collections.singletonMap(parent.getName(), null);
          }
        }
        else {
          return Collections.singletonMap(FileUtilRt.getNameWithoutExtension(name), null);
        }
        return Collections.emptyMap();
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileTypeInputFilterPredicate(BEFORE_SUBSTITUTION, fileType -> fileType == PythonFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @NotNull
  public static Collection<String> getAllKeys(@NotNull Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }

  @NotNull
  public static List<PyFile> find(@NotNull String shortName, @NotNull Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? PySearchUtilBase.excludeSdkTestsScope(project)
                                                           : GlobalSearchScope.projectScope(project);
    return findByShortName(shortName, project, scope);
  }

  /**
   * Returns all modules with the given short name (the last component of a fully qualified name).
   * <p>
   * File extensions should not be included. For __init__.py modules, the name of the corresponding directory is matched.
   *
   * @param shortName short name of a module or name of the containing package for __init__.py modules
   * @param project   project where the search is performed
   * @param scope     search scope, limiting applicable virtual files
   */
  @NotNull
  public static List<PyFile> findByShortName(@NotNull String shortName, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    final List<PyFile> results = new ArrayList<>();
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, shortName, scope);
    for (VirtualFile virtualFile : files) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile instanceof PyFile) {
        results.add((PyFile)psiFile);
      }
    }
    return results;
  }

  /**
   * Returns all modules with the given fully qualified name.
   * <p>
   * For __init__.py modules, the qualified name of the corresponding package is used.
   * <p>
   * All possible qualified names of a module are considered. For instance, in case of a source root "src" inside a project's
   * content root, src/foo.py or src/foo/__init__.py will be returned both for qualified names "foo" and "src.foo".
   *
   * @param qName   short name of a module or name of the containing package for __init__.py modules
   * @param project project where the search is performed
   * @param scope   search scope, limiting applicable virtual files
   * @see QualifiedNameFinder#findImportableQNames(PsiElement, VirtualFile)
   */
  @NotNull
  public static List<PyFile> findByQualifiedName(@NotNull QualifiedName qName, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    String shortName = qName.getLastComponent();
    if (shortName == null) return Collections.emptyList();
    return ContainerUtil.filter(findByShortName(shortName, project, scope), file -> {
      List<QualifiedName> possibleQNames = QualifiedNameFinder.findImportableQNames(file, file.getVirtualFile());
      return possibleQNames.contains(qName);
    });
  }
}
