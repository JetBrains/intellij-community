/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.10.2006
 * Time: 16:03:23
 */
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;

public class FormEnumUsageTest extends PsiTestCase {
  private VirtualFile myTestProjectRoot;

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String root = PathManagerEx.getTestDataPath() + "/uiDesigner/binding/" + getTestName(true);
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.5"));
            myTestProjectRoot = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  @Override protected void tearDown() throws Exception {
    myTestProjectRoot = null;
    super.tearDown();
  }

  public void testEnumUsage() throws IncorrectOperationException {
    myPsiManager.setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          createFile(myModule, myTestProjectRoot, "PropEnum.java", "public enum PropEnum { valueA, valueB }");
          createFile(myModule, myTestProjectRoot, "CustomComponent.java", "public class CustomComponent extends JLabel { private PropEnum e; public PropEnum getE() { return e; } public void setE(E newE) { e = newE; } }");
        }
        catch (Exception e) {
          fail(e.getMessage());
        }
      }
    }, "", null);

    PsiClass enumClass = myPsiManager.findClass("PropEnum", myProject.getAllScope());
    PsiField valueBField = enumClass.findFieldByName("valueB", false);
    assertNotNull(valueBField);
    assertTrue(valueBField instanceof PsiEnumConstant);
    final PsiClass componentClass = myPsiManager.findClass("CustomComponent", myProject.getAllScope());
    assertNotNull(componentClass);

    Query<PsiReference> query = ReferencesSearch.search(componentClass);
    assertEquals(1, query.findAll().size());

    query = ReferencesSearch.search(valueBField);
    assertEquals(1, query.findAll().size());
  }

}