// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author vlan
 */
public class PyModuleNameIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> NAME = ID.create("Py.module.name");

  private final DataIndexer<String, Void, FileContent> myDataIndexer = new DataIndexer<String, Void, FileContent>() {
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

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
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
    return new DefaultFileTypeSpecificInputFilter(PythonFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
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
  public static List<PyFile> find(@NotNull String name, @NotNull Project project, boolean includeNonProjectItems) {
    final List<PyFile> results = new ArrayList<>();
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? PySearchUtilBase.excludeSdkTestsScope(project)
                                    : GlobalSearchScope.projectScope(project);
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, name, scope);
    for (VirtualFile virtualFile : files) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile instanceof PyFile) {
        if (!PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(psiFile)) {
          results.add((PyFile)psiFile);
        }
      }
    }
    return results;
  }
}
