/*
 * @author max
 */
package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.stubs.PyClassStub;

import java.util.List;

public class PyStubsTest extends CodeInsightTestCase {
  private VirtualFile myRootDir;

  protected void setUp() throws Exception {
    myRunCommandForTest = false;
    super.setUp();
    prepareRoots();
  }



  private void prepareRoots() throws Exception {
    new WriteAction() {
      protected void run(final Result result) throws Throwable {
        String root = PathManager.getHomePath() + "/plugins/python/testData/stubs/";
        myRootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);
        PsiTestUtil.addSourceContentToRoots(myModule, myRootDir);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      }
    }.execute();
  }

  public void testStubStructure() throws Exception {
    final PyFile file = getTestFile();
    final List<PyClass> classes = file.getTopLevelClasses();
    assertEquals(1, classes.size());
    PyClass pyClass = classes.get(0);
    assertEquals("FooClass", pyClass.getName());

    final PyTargetExpression[] attrs = pyClass.getClassAttributes();
    assertEquals(1, attrs.length);
    assertEquals("staticField", attrs [0].getName());

    final PyFunction[] methods = pyClass.getMethods();
    assertEquals(2, methods.length);
    assertEquals("__init__", methods [0].getName());
    assertEquals("fooFunction", methods [1].getName());

    final PyTargetExpression[] instanceAttrs = pyClass.getInstanceAttributes();
    assertEquals(1, instanceAttrs.length);
    assertEquals("instanceField", instanceAttrs [0].getName());

    final List<PyFunction> functions = file.getTopLevelFunctions();
    assertEquals(1, functions.size());
    PyFunction func = functions.get(0);

    assertEquals("topLevelFunction", func.getName());

    final List<PyTargetExpression> exprs = file.getTopLevelAttributes();
    assertEquals(2, exprs.size());
    assertEquals("top1", exprs.get(0).getName());
    assertEquals("top2", exprs.get(1).getName());

    assertNull("Operations should have been performed on stubs but caused file to be parsed", ((PyFileImpl)file).getTreeElement());
  }
  
  public void testLoadingDeeperTreeRemainsKnownPsiElement() throws Exception {
    final PyFile file = getTestFile();
    final List<PyClass> classes = file.getTopLevelClasses();
    assertEquals(1, classes.size());
    PyClass pyClass = classes.get(0);

    assertEquals("SomeClass", pyClass.getName());

    assertNull("Operations should have been performed on stubs but caused file to be parsed", ((PyFileImpl)file).getTreeElement());

    // load the tree now
    final PyStatementList statements = pyClass.getStatementList();
    assertNotNull(((PyFileImpl)file).getTreeElement());

    final PsiElement[] children = file.getChildren();

    assertEquals(1, children.length);
    assertSame(pyClass, children[0]);
  }

  public void testLoadingTreeRetainsKnownPsiElement() throws Exception {
    final PyFile file = getTestFile();
    final List<PyClass> classes = file.getTopLevelClasses();
    assertEquals(1, classes.size());
    PyClass pyClass = classes.get(0);

    assertEquals("SomeClass", pyClass.getName());

    assertNull("Operations should have been performed on stubs but caused file to be parsed", ((PyFileImpl)file).getTreeElement());

    final PsiElement[] children = file.getChildren(); // Load the tree

    assertNotNull(((PyFileImpl)file).getTreeElement());
    assertEquals(1, children.length);
    assertSame(pyClass, children[0]);
  }

  public void testRenamingUpdatesTheStub() throws Exception {
    final PyFile file = getTestFile("LoadingTreeRetainsKnownPsiElement.py");
    final List<PyClass> classes = file.getTopLevelClasses();
    assertEquals(1, classes.size());
    final PyClass pyClass = classes.get(0);

    assertEquals("SomeClass", pyClass.getName());

    // Ensure we haven't loaded the tree yet.
    final PyFileImpl fileImpl = (PyFileImpl)file;
    assertNull(fileImpl.getTreeElement());

    final PsiElement[] children = file.getChildren(); // Load the tree

    assertNotNull(fileImpl.getTreeElement());
    assertEquals(1, children.length);
    assertSame(pyClass, children[0]);

    new WriteCommandAction(myProject, fileImpl) {
      protected void run(final Result result) throws Throwable {
        pyClass.setName("RenamedClass");
        assertEquals("RenamedClass", pyClass.getName());
      }
    }.execute();

    StubElement fileStub = fileImpl.getStub();
    assertNull("There should be no stub if file holds tree element", fileStub);
    
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID);
    fileImpl.unloadContent();
    assertNull(fileImpl.getTreeElement()); // Test unload successed.

    fileStub = fileImpl.getStub();
    assertNotNull("After tree element have been unloaded we must be able to create updated stub", fileStub);

    final PyClassStub newclassstub = (PyClassStub)fileStub.getChildrenStubs().get(0);
    assertEquals("RenamedClass", newclassstub.getName());
  }

  private PyFile getTestFile() throws Exception {
    return getTestFile(getTestName(false) + ".py");
  }

  private PyFile getTestFile(final String fileName) {
    VirtualFile sourceFile = myRootDir.findChild(fileName);
    assert sourceFile != null;
    PsiFile psiFile = myPsiManager.findFile(sourceFile);
    return (PyFile)psiFile;
  }
}