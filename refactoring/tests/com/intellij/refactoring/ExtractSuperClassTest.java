package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.idea.IdeaTestUtil;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

/**
 * @author yole
 */
public class ExtractSuperClassTest extends CodeInsightTestCase {
  public void testFinalFieldInitialization() throws Exception {   // IDEADEV-19704
    doTest(new Pair<String, Class<? extends PsiMember>>("X", PsiClass.class),
           new Pair<String, Class<? extends PsiMember>>("x", PsiField.class));
  }

  public void testParameterNameEqualsFieldName() throws Exception {    // IDEADEV-10629
    doTest(new Pair<String, Class<? extends PsiMember>>("a", PsiField.class));
  }

  public void testExtendsLibraryClass() throws Exception {
    doTest();
  }

  private String getRoot() {
    return PathManagerEx.getTestDataPath()+ "/refactoring/extractSuperClass/" + getTestName(true);
  }

  private void doTest(Pair<String, Class<? extends PsiMember>>... membersToFind) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiClass psiClass = myPsiManager.findClass("Test", myProject.getAllScope());
    assertNotNull(psiClass);
    final MemberInfo[] members = PullUpTest.findMembers(psiClass, membersToFind);
    ExtractSuperClassProcessor processor = new ExtractSuperClassProcessor(myProject,
                                                                          psiClass.getContainingFile().getContainingDirectory(),
                                                                          "TestSubclass",
                                                                          psiClass, members,
                                                                          false,
                                                                          new JavaDocPolicy(JavaDocPolicy.ASIS));
    processor.run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, MultiFileTestCase.CVS_FILE_FILTER);
  }
}