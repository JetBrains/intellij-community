/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.util.Query;

import javax.swing.*;
import java.util.Collection;

/**
 * @author yole
 */
public class FormPropertyUsageTest extends PsiTestCase {
  private VirtualFile myTestProjectRoot;

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String root = PathManagerEx.getTestDataPath() + "/uiDesigner/binding/" + getTestName(true);
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
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

  public void testClassUsage() {
    PsiClass psiClass = myPsiManager.findClass(JButton.class.getName(), GlobalSearchScope.allScope(myProject));
    final Query<PsiReference> query = ReferencesSearch.search(psiClass);
    final Collection<PsiReference> result = query.findAll();
    assertEquals(1, result.size());
  }

  public void testFormPropertyUsage() {
    doPropertyUsageTest("test.properties");
  }

  public void testLocalizedPropertyUsage() {
    doPropertyUsageTest("test_ru.properties");
  }

  private void doPropertyUsageTest(final String propertyFileName) {
    PropertiesFile propFile = (PropertiesFile) myPsiManager.findFile(myTestProjectRoot.findChild(propertyFileName));
    assertNotNull(propFile);
    final Property prop = propFile.findPropertyByKey("key");
    assertNotNull(prop);
    final Query<PsiReference> query = ReferencesSearch.search(prop);
    final Collection<PsiReference> result = query.findAll();
    assertEquals(1, result.size());
    verifyReference(result, 0, "form.form", 960);
  }

  public void testPropertyFileUsage() {
    doPropertyFileUsageTest("test.properties");
  }

  public void testLocalizedPropertyFileUsage() {
     doPropertyFileUsageTest("test_ru.properties");
  }

  private void doPropertyFileUsageTest(final String fileName) {
    PropertiesFile propFile = (PropertiesFile) myPsiManager.findFile(myTestProjectRoot.findChild(fileName));
    assertNotNull(propFile);
    final Query<PsiReference> query = ReferencesSearch.search(propFile);
    final Collection<PsiReference> result = query.findAll();
    assertEquals(1, result.size());
    verifyReference(result, 0, "form.form", 949);
  }

  private void verifyReference(final Collection<PsiReference> result, final int index, final String fileName, final int offset) {
    PsiReference ref = result.toArray(new PsiReference[result.size()]) [index];
    final PsiElement element = ref.getElement();
    assertEquals(fileName, element.getContainingFile().getName());
    int startOffset = element.getTextOffset() + ref.getRangeInElement().getStartOffset();
    assertEquals(offset, startOffset);
  }

}
