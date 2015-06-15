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

/*
 * User: anna
 * Date: 30-Jul-2007
 */
package com.theoryinpractice.testng.intention;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

public class TestNGOrderEntryFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + TestNGOrderEntryFix.class.getName());

  @NotNull
  public String getText() {
    return "Add testng.jar to classpath";
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (!file.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;

    final PsiReference reference = TargetElementUtil.findReference(editor);
    if (!(reference instanceof PsiJavaCodeReferenceElement)) return false;
    if (reference.resolve() != null) return false;
    @NonNls final String referenceName = ((PsiJavaCodeReferenceElement)reference).getReferenceName();
    if (referenceName == null) return false;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (fileIndex.getModuleForFile(virtualFile) == null) return false;
    if (!(((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnnotation &&
          PsiUtil.isLanguageLevel5OrHigher(((PsiJavaCodeReferenceElement)reference)))) return false;
    if (!isTestNGAnnotationName(referenceName)) return false;
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)TargetElementUtil.findReference(editor);
    LOG.assertTrue(reference != null);
    String jarPath = PathUtil.getJarPathForClass(Test.class);
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    OrderEntryFix.addBundledJarToRoots(project, editor, ModuleUtilCore.findModuleForFile(virtualFile, project), reference,
                                       "org.testng.annotations." + reference.getReferenceName(), jarPath);
  }

  public boolean startInWriteAction() {
    return true;
  }

  private static boolean isTestNGAnnotationName(@NonNls final String referenceName) {
    return "Test".equals(referenceName) ||
           "BeforeClass".equals(referenceName) ||
           "BeforeGroups".equals(referenceName) ||
           "BeforeMethod".equals(referenceName) ||
           "BeforeSuite".equals(referenceName) ||
           "BeforeTest".equals(referenceName) ||
           "AfterClass".equals(referenceName) ||
           "AfterGroups".equals(referenceName) ||
           "AfterMethod".equals(referenceName) ||
           "AfterSuite".equals(referenceName) ||
           "AfterTest".equals(referenceName) ||
           "Configuration".equals(referenceName);

  }
}