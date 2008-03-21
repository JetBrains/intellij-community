package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

import java.util.Collection;

/**
 * @author yole
 */
public class PyInheritorsSearchTest extends CodeInsightTestCase {
  public void testSimple() throws Exception {
    setupProject();
    final PyClass pyClass = findClass("A");
    Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass).findAll();
    assertEquals(2, inheritors.size());
  }

  private void setupProject() throws Exception {
    String testName = getTestName(true);
    String root = PathManager.getHomePath() + "/plugins/python/testData/inheritors/" + testName;
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  private PyClass findClass(final String name) {
    final Collection<PyClass> classes = StubIndex.getInstance().get(PyClassNameIndex.KEY, name, myProject,
                                                                    ProjectScope.getAllScope(myProject));
    assert classes.size() == 1;
    return classes.iterator().next();
  }
}
