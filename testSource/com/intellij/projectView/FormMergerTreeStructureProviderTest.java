package com.intellij.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.ide.projectView.impl.FormMergerTreeStructureProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;


public class FormMergerTreeStructureProviderTest extends BaseProjectViewTestCase {

  public void testStandardProviders() {
    getProjectTreeStructure().setProviders(new TreeStructureProvider[]{
      new ClassesTreeStructureProvider(myProject),
      new FormMergerTreeStructureProvider(myProject)
    });

    assertStructureEqual(getPackageDirectory(), "PsiDirectory: package1\n" +
                                                                      " Form:Form1\n" +
                                                                      "  PsiClass:Form1\n" +
                                                                      "  PsiFile(plain text):Form1.form\n" +
                                                                      " PsiClass:Class1\n" +
                                                                      " PsiClass:Class2\n" +
                                                                      " PsiClass:Class3\n" +
                                                                      " PsiFile(plain text):Form2.form\n" +
                                                                      " PsiJavaFile:Class4.java\n");

    PsiClass psiClass = ((PsiJavaFile)getPackageDirectory().findFile("Form1.java")).getClasses()[0];
    checkNavigateFromSourceBehaviour(psiClass, psiClass.getContainingFile().getVirtualFile());

    PsiFile psiFile = getPackageDirectory().findFile("Form1.form");
    VirtualFile virtualFile = psiFile.getContainingFile().getVirtualFile();
    checkNavigateFromSourceBehaviour(psiFile, virtualFile);    
  }

}
