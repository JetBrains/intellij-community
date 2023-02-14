// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringTest;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public final class PyExtractSuperclassTest extends PyClassRefactoringTest {

  public PyExtractSuperclassTest() {
    super("extractsuperclass");
  }

  // PY-21099
  public void testClassPropertyDependsOnMethod() {
    doSimpleTest("C", "Spam", null, true, true, ".__add__", "#__radd__");
  }

  // Checks if class explicitly extends object we shall move it even in Py3K (PY-19137)
  public void testPy3ParentHasObject() {
    doSimpleTest("Child", "Parent", null, true, false, ".spam");
  }

  // Ensures refactoring works even if memeberInfo has null element (no npe: PY-19136)
  public void testFieldsNpe() {
    doSimpleTest("Basic", "Ancestor", null, true, false, ".__init__", "#a", "#b", ".func1");
  }

  // Checks that moving methods between files moves imports as well
  public void testImportMultiFile() {
    multiFileTestHelper(".do_useful_stuff", false);
  }

  // Checks that moving methods between files moves superclass expressions as well
  public void testMoveExtends() {
    multiFileTestHelper("TheParentOfItAll", false);
  }

  // Checks that moving methods between files moves superclass expressions regardless import style (q.name or name)
  public void testMoveExtendsCheckReference() {
    multiFileTestHelper("TheParentOfItAll", false);
  }

  // Extracts method as abstract
  public void testMoveAndMakeAbstract() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      multiFileTestHelper(".foo_method", true);
    });
  }

  // Extracts method as abstract and ensures that newly created class imports ABC in Py3
  public void testMoveAndMakeAbstractImportExistsPy3() {
    multiFileTestHelper(".foo_method", true);
  }

  /**
   * Moves member from class <pre>MyClass</pre> in module <pre>source_module</pre> to class <pre>NewParent</pre> in module <pre>dest_module</pre>.
   * Ensures it is moved correctly.
   *
   * @param memberToMove name of the member to move
   */
  private void multiFileTestHelper(@NotNull final String memberToMove, final boolean toAbstract) {
    final String[] modules = {"dest_module", "source_module"};
    configureMultiFile(ArrayUtil.mergeArrays(modules, "shared_module"));
    myFixture.configureByFile("source_module.py");
    final String sourceClass = "MyClass";
    final PyMemberInfo<PyElement> member = findMemberInfo(sourceClass, memberToMove);
    member.setToAbstract(toAbstract);
    final String destUrl = myFixture.getFile().getVirtualFile().getParent().findChild("dest_module.py").getUrl();
    WriteCommandAction.writeCommandAction(myFixture.getProject()).run(
      () -> PyExtractSuperclassHelper.extractSuperclass(findClass(sourceClass), Collections.singleton(member), "NewParent", destUrl));
    checkMultiFile(modules);
  }

  public void testSimple() {
    doSimpleTest("Foo", "Suppa", null, true, false, ".foo");
  }

  public void testInstanceNotDeclaredInInit() {
    doSimpleTest("Child", "Parent", null, true, false, "#eggs");
  }

  public void testWithSuper() {
    doSimpleTest("Foo", "Suppa", null, true, false, ".foo");
  }

  public void testWithImport() {
    doSimpleTest("A", "Suppa", null, false, false, ".foo");
  }

  // PY-12175
  public void testImportNotBroken() {
    myFixture.copyFileToProject("refactoring/extractsuperclass/shared.py", "shared.py");
    doSimpleTest("Source", "DestClass", null, true, false, "SharedClass");
  }

  // PY-12175 but between several files
  public void testImportNotBrokenManyFiles() {
    multiFileTestHelper("SharedClass", false);
  }

  public void testMoveFields() {
    doSimpleTest("FromClass", "ToClass", null, true, false, "#instance_field", "#CLASS_FIELD");
  }


  public void testProperties() {
    doSimpleTest("FromClass", "ToClass", null, true, false, "#C", "#a", "._get", ".foo");
  }

  // PY-16747
  public void testAbstractMethodDocStringIndentationPreserved() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doSimpleTest("B", "A", null, true, true, ".m");
    });
  }

  // PY-16770
  public void testAbstractMethodDocStringPrefixPreserved() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doSimpleTest("B", "A", null, true, true, ".m");
    });
  }

  private void doSimpleTest(final String className,
                            final String superclassName,
                            final String expectedError,
                            final boolean sameFile,
                            boolean asAbstract, final String... membersName) {
    try {
      String baseName = "refactoring/extractsuperclass/" + getTestName(true);
      myFixture.configureByFile(baseName + ".before.py");
      final PyClass clazz = findClass(className);
      final List<PyMemberInfo<PyElement>> members = new ArrayList<>();
      for (String memberName : membersName) {
        final PyElement member = findMember(className, memberName);
        final PyMemberInfo<PyElement> memberInfo = MembersManager.findMember(clazz, member);
        memberInfo.setToAbstract(asAbstract);
        members.add(memberInfo);
      }

      WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> {
        final String url = sameFile ? myFixture.getFile().getVirtualFile().getUrl() :
                           myFixture.getFile().getVirtualFile().getParent().getUrl();
        PyExtractSuperclassHelper.extractSuperclass(clazz, members, superclassName, url);
      });
      myFixture.checkResultByFile(baseName + ".after.py");
    }
    catch (Exception e) {
      if (expectedError == null) throw e;
      assertEquals(expectedError, e.getMessage());
    }
  }

  public void testMultifileNew() {
    String baseName = "refactoring/extractsuperclass/multifile/";
    myFixture.configureByFile(baseName + "source.py");
    final String className = "Foo";
    final String superclassName = "Suppa";
    final PyClass clazz = findClass(className);
    final List<PyMemberInfo<PyElement>> members = new ArrayList<>();
    final PyElement member = findMember(className, ".foo");
    members.add(MembersManager.findMember(clazz, member));
    final VirtualFile base_dir = myFixture.getFile().getVirtualFile().getParent();

    WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> {
      final String path = base_dir.getPath() + "/a/b";
      PyExtractSuperclassHelper.extractSuperclass(clazz, members, superclassName, path);
    });
    final PsiManager psi_mgr = PsiManager.getInstance(myFixture.getProject());
    VirtualFile vfile = base_dir.findChild("a");
    assertTrue(vfile.isDirectory());
    vfile = vfile.findChild(PyNames.INIT_DOT_PY);
    assertNotNull(vfile);

    vfile = base_dir.findChild("a").findChild("b");
    assertTrue(vfile.isDirectory());
    vfile = vfile.findChild(PyNames.INIT_DOT_PY);
    assertNotNull(vfile);

    PsiFile psi_file = psi_mgr.findFile(vfile);
    String result = psi_file.getText().trim();
    File expected_file = new File(getTestDataPath(), baseName + "target.new.py");
    String expected = psi_mgr.findFile(LocalFileSystem.getInstance().findFileByIoFile(expected_file)).getText().trim();
    assertEquals(expected, result);
  }

  public void testMultifileAppend() {
    // this is half-copy-paste of testMultifileNew. generalization won't make either easier to follow.
    String baseName = "refactoring/extractsuperclass/multifile/";
    myFixture.configureByFiles(
      baseName + "source.py",
      baseName + "a/__init__.py",
      baseName + "a/b/__init__.py",
      baseName + "a/b/foo.py"
    );
    final String className = "Foo";
    final String superclassName = "Suppa";
    final PyClass clazz = findClass(className);
    final List<PyMemberInfo<PyElement>> members = new ArrayList<>();
    final PyElement member = findMember(className, ".foo");
    members.add(MembersManager.findMember(clazz, member));
    final VirtualFile base_dir = myFixture.getFile().getVirtualFile().getParent();

    WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> {
      //TODO: Test via presenter
      final String path = base_dir.getPath() + "/a/b";
      PyExtractSuperclassHelper.extractSuperclass(clazz, members, superclassName, path + "/foo.py");
    });
    final PsiManager psi_mgr = PsiManager.getInstance(myFixture.getProject());
    VirtualFile vfile = base_dir.findChild("a");
    assertTrue(vfile.isDirectory());
    vfile = vfile.findChild(PyNames.INIT_DOT_PY);
    assertNotNull(vfile);

    vfile = base_dir.findChild("a").findChild("b");
    assertTrue(vfile.isDirectory());
    assertNotNull(vfile.findChild(PyNames.INIT_DOT_PY));
    vfile = vfile.findChild("foo.py");
    assertNotNull(vfile);

    PsiFile psi_file = psi_mgr.findFile(vfile);
    String result = psi_file.getText().trim();
    File expected_file = new File(getTestDataPath(), baseName + "target.append.py");
    String expected = psi_mgr.findFile(LocalFileSystem.getInstance().findFileByIoFile(expected_file)).getText().trim();
    assertEquals(expected, result);
  }

  // PY-46099
  public void testNoClassCastExceptionInCopiedFunctionWithClassInitAndMethodCall() {
    doSimpleTest("Baz", "Bar", null, true, false, ".baz");
  }

  // PY-16221
  public void testFromFutureImports() {
    multiFileTestHelper(".foo", false);
  }
}