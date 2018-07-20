// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.Query;

import javax.swing.*;
import java.util.Collection;

/**
 * @author yole
 */
public class FormPropertyUsageTest extends PsiTestCase {
  private VirtualFile myTestProjectRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/binding/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myTestProjectRoot = createTestProjectStructure(root);
  }

  @Override protected void tearDown() throws Exception {
    myTestProjectRoot = null;
    super.tearDown();
  }

  public void testClassUsage() {
    PsiClass psiClass = myJavaFacade.findClass(JButton.class.getName(), GlobalSearchScope.allScope(myProject));
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
    final Property prop = (Property)propFile.findPropertyByKey("key");
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
    final Query<PsiReference> query = ReferencesSearch.search(propFile.getContainingFile());
    final Collection<PsiReference> result = query.findAll();
    assertEquals(1, result.size());
    verifyReference(result, 0, "form.form", 949);
  }

  private void verifyReference(final Collection<PsiReference> result, final int index, final String fileName, final int offset) {
    PsiReference ref = result.toArray(PsiReference.EMPTY_ARRAY) [index];
    final PsiElement element = ref.getElement();
    assertEquals(fileName, element.getContainingFile().getName());
    int startOffset = element.getTextOffset() + ref.getRangeInElement().getStartOffset();
    assertEquals(offset, startOffset);
  }

}
