/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WizardData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.WizardData");

  @NotNull public final Project myProject;
  /**
   * Form's file.
   */
  @NotNull public final VirtualFile myFormFile;

  /**
   * If {@code true} then {@link #myShortClassName} and {@link #myPackageName} should be
   * used, otherwise {@link #myBeanClass} should be used.
   */
  public boolean myBindToNewBean;
  /**
   *
   */
  public String myShortClassName;
  /**
   *
   */
  public String myPackageName;

  /**
   * Bean's class. If {@code null} then bean's class is't defined yet.
   */
  public PsiClass myBeanClass;
  @NotNull public final FormProperty2BeanProperty[] myBindings;

  public boolean myGenerateIsModified;

  public WizardData(@NotNull final Project project, @NotNull final VirtualFile formFile) throws Generator.MyException {
    myProject = project;
    myFormFile = formFile;
    myBindToNewBean = true;
    myGenerateIsModified = true;

    final LwRootContainer[] rootContainer = new LwRootContainer[1];

    // Create initial bingings between form fields and bean's properties.
    // TODO[vova] ask Anton to not throw exception if form-field doesn't have corresponded field in the Java class
    final FormProperty[] formProperties = Generator.exposeForm(myProject, myFormFile, rootContainer);
    myBindings = new FormProperty2BeanProperty[formProperties.length];
    for(int i = formProperties.length - 1; i >= 0; i--){
      myBindings[i] = new FormProperty2BeanProperty(formProperties[i]);
    }

    final PsiManager manager = PsiManager.getInstance(myProject);
    final VirtualFile directory = formFile.getParent();
    LOG.assertTrue(directory.isDirectory());
    final PsiDirectory psiDirectory = manager.findDirectory(directory);
    LOG.assertTrue(psiDirectory != null);
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    if(aPackage != null){
      myPackageName = aPackage.getQualifiedName();
    }
  }
}
