// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

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
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
    List<VirtualFile> files = new ArrayList<>();
    for (IndexPattern indexPattern : IndexPatternUtil.getIndexPatterns()) {
      files.addAll(fileBasedIndex.getContainingFiles(
        TodoIndex.NAME,
        new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), GlobalSearchScope.allScope(project)));
    }
    return files;
  }

  public void testTodoIndexInLibs() {
    final String libraryWithTodoName = "numbers.py";

    runWithAdditionalFileInLibDir(
      libraryWithTodoName,
      "# TODO: this should be updated",
      (__) -> {
        final List<VirtualFile> indexFiles = getTodoFiles(myFixture.getProject());

        // project file in the TodoIndex
        assertTrue(indexFiles.stream().anyMatch((x) -> "a.py".equals(x.getName())));

        // no library files in the TodoIndex
        assertFalse(indexFiles.stream().anyMatch((x) -> libraryWithTodoName.equals(x.getName())));

        final Module module = myFixture.getModule();
        final Sdk sdk = PythonSdkUtil.findPythonSdk(module);

        final VirtualFile libDir = PySearchUtilBase.findLibDir(sdk);

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
    );
  }

  // PY-19047
  public void testPy19047() {
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, new Throwable());
  }
}
