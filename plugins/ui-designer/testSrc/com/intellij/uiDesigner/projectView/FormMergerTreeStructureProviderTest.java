/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

  @NotNull
  @Override
  protected String getTestDirectoryName() {
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
