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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.fixtures.PyTestCase;
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
    Sdk sdk = PythonSdkType.findPythonSdk(myFixture.getModule());

    PsiFile file = myFixture.addFileToProject("libs/smtpd.py", "# TODO: fix it");
    VirtualFile root = sdk.getRootProvider().getFiles(OrderRootType.CLASSES)[0];
    VirtualFile libsRoot = file.getVirtualFile().getParent();

    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
    ContentEntry[] entries = modifiableModel.getContentEntries();

    modifiableModel.clear();
    modifiableModel.addContentEntry(myFixture.findFileInTempDir("project"));

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.addRoot(libsRoot, OrderRootType.CLASSES);

    ApplicationManager.getApplication().runWriteAction(() -> {
      modifiableModel.commit();
      sdkModificator.commitChanges();
    });

    try {
      List<VirtualFile> indexFiles = getTodoFiles(myFixture.getProject());

      // project file in the TodoIndex
      assertTrue(indexFiles.stream().anyMatch((x) -> "a.py".equals(x.getName())));

      // no library files in the TodoIndex
      assertFalse(indexFiles.stream().anyMatch((x) -> "smtpd.py".equals(x.getName())));

      ModuleRootModificationUtil.addContentRoot(myFixture.getModule(), libsRoot);

      indexFiles = getTodoFiles(myFixture.getProject());

      // but if it is added as a content root - it should be in the TodoIndex
      assertTrue(indexFiles.stream().anyMatch((x) -> "smtpd.py".equals(x.getName())));
    }
    finally {
      // revert changes to sdk roots
      SdkModificator modificator = sdk.getSdkModificator();
      modificator.removeRoot(libsRoot, OrderRootType.CLASSES);
      modificator.addRoot(root, OrderRootType.CLASSES);

      ModifiableRootModel m = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();

      m.clear();

      for (ContentEntry e : entries) {
        final ContentEntry newEntry = m.addContentEntry(e.getFile());
        for (SourceFolder folder : e.getSourceFolders()) {
          newEntry.addSourceFolder(folder.getFile(), folder.isTestSource());
        }
        for (ExcludeFolder folder : e.getExcludeFolders()) {
          newEntry.addExcludeFolder(folder.getFile());
        }
        for (String pattern : e.getExcludePatterns()) {
          newEntry.addExcludePattern(pattern);
        }
      }

      ApplicationManager.getApplication().runWriteAction(() -> {
        modificator.commitChanges();
        m.commit();
      });
    }
  }

  // PY-19047
  public void testPy19047() {
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, new Throwable());
  }
}
