/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 17-Oct-2007
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.refactoring.introduceField.LocalToFieldHandler;
import com.intellij.util.PathUtil;
import junit.framework.TestCase;

import java.io.File;

public class IntroduceFieldWitSetUpInitializationTest extends CodeInsightTestCase {
  protected Module createModule(final String path) {
    final Module module = super.createModule(path);
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final LibraryTable.ModifiableModel modifiableModel = model.getModuleLibraryTable().getModifiableModel();
    final Library library = modifiableModel.createLibrary("junit");
    final Library.ModifiableModel libModel = library.getModifiableModel();
    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(new File(PathUtil.getJarPathForClass(TestCase.class))), OrderRootType.CLASSES);
    libModel.commit();
    model.commit();
    return module;
  }

  protected boolean clearModelBeforeConfiguring() {
    return false;
  }

  public void testInSetUp() throws Exception {
    doTest();
  }

  public void testPublicBaseClassSetUp() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    configureByFile("/refactoring/introduceField/before" + getTestName(false) + ".java");
    final PsiLocalVariable local =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new LocalToFieldHandler(getProject(), false) {
      protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(final PsiClass aClass,
                                                                            final PsiLocalVariable local,
                                                                            final PsiExpression[] occurences,
                                                                            final boolean isStatic) {
        return new BaseExpressionToFieldHandler.Settings("i", true, false, false,
                                                         BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD,
                                                         PsiModifier.PRIVATE, local, local.getType(), true, null, false);
      }
    }.convertLocalToField(local, myEditor);
    checkResultByFile("/refactoring/introduceField/after" + getTestName(false)+ ".java");
  }
}