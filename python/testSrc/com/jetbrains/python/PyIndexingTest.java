/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
public class PyIndexingTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "indexing/";

  @Override
  protected void setUp() throws Exception {

    super.setUp();
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
    myFixture.configureFromTempProjectFile("a.py");
  }

  public void testModuleNameIndex() {
    final Collection<String> modules = PyModuleNameIndex.getAllKeys(myFixture.getProject());
    assertContainsElements(modules, "ModuleNameIndex_foo");
    assertContainsElements(modules, "ModuleNameIndex_bar");
    assertDoesntContain(modules, "__init__");
    assertDoesntContain(modules, "ModuleNameIndex_baz");
  }

  private static List<VirtualFile> getTodoFiles(@NotNull Project project) {
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    List<VirtualFile> files = Lists.newArrayList();
    for (IndexPattern indexPattern : IndexPatternUtil.getIndexPatterns()) {
      files.addAll(fileBasedIndex.getContainingFiles(
        TodoIndex.NAME,
        new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), GlobalSearchScope.allScope(project)));
    }
    return files;
  }

  public void testTodoIndexInLibs() {
    final String libraryWithTodoName = "numbers.py";
    final List<VirtualFile> indexFiles = getTodoFiles(myFixture.getProject());

    // project file in the TodoIndex
    assertTrue(indexFiles.stream().anyMatch((x) -> "a.py".equals(x.getName())));

    // no library files in the TodoIndex
    assertFalse(indexFiles.stream().anyMatch((x) -> libraryWithTodoName.equals(x.getName())));

    final Module module = myFixture.getModule();
    final Sdk sdk = PythonSdkType.findPythonSdk(module);

    final VirtualFile libDir = PyProjectScopeBuilder.findLibDir(sdk);

    ModuleRootModificationUtil.addContentRoot(module, libDir);
    try {
      // mock sdk doesn't fire events
      FileBasedIndex.getInstance().requestRebuild(TodoIndex.NAME);
      FileBasedIndex.getInstance().ensureUpToDate(TodoIndex.NAME, myFixture.getProject(), null);
      final List<VirtualFile> updatedIndexFiles = getTodoFiles(myFixture.getProject());
      // but if it is added as a content root - it should be in the TodoIndex
      assertTrue(updatedIndexFiles.stream().anyMatch((x) -> libraryWithTodoName.equals(x.getName())));
    }
    finally {
      ModuleRootModificationUtil.updateModel(module, model -> {
        for (ContentEntry entry : model.getContentEntries()) {
          if (libDir.equals(entry.getFile())) {
            model.removeContentEntry(entry);
          }
        }
      });
    }
  }

  // PY-19047
  public void testPy19047() {
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, new Throwable());
  }
}
