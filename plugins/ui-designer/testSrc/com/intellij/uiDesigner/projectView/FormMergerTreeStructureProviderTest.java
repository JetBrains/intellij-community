// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.BaseProjectViewTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;


public class FormMergerTreeStructureProviderTest extends BaseProjectViewTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData";
  }

  public void testStandardProviders() {
    final AbstractProjectViewPane pane = myStructure.createPane();
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject),
                                           new FormMergerTreeStructureProvider(myProject));

    assertStructureEqual(getPackageDirectory(), """
      PsiDirectory: package1
       PsiClass:Class1
       PsiJavaFile:Class2.java
        PsiClass:Class2
        PsiClass:Class3
       PsiJavaFile:Class4.java
       PsiFile(plain text):Form2.form
       Form:Form1
        PsiClass:Form1
        PsiFile(plain text):Form1.form
      """);

    PsiClass psiClass = ((PsiJavaFile)getPackageDirectory().findFile("Form1.java")).getClasses()[0];
    myStructure.checkNavigateFromSourceBehaviour(psiClass, psiClass.getContainingFile().getVirtualFile(), pane);
  }

  @Override
  protected @NotNull String getTestDirectoryName() {
    return "standardProviders";
  }

  public void testStandardProvidersForm1() {
    final AbstractProjectViewPane pane = myStructure.createPane();
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject),
                                           new FormMergerTreeStructureProvider(myProject));

    assertStructureEqual(getPackageDirectory(), """
      PsiDirectory: package1
       PsiClass:Class1
       PsiJavaFile:Class2.java
        PsiClass:Class2
        PsiClass:Class3
       PsiJavaFile:Class4.java
       PsiFile(plain text):Form2.form
       Form:Form1
        PsiClass:Form1
        PsiFile(plain text):Form1.form
      """);

    PsiFile psiFile = getPackageDirectory().findFile("Form1.form");
    VirtualFile virtualFile = psiFile.getContainingFile().getVirtualFile();
    myStructure.checkNavigateFromSourceBehaviour(psiFile, virtualFile, pane);
  }

}
