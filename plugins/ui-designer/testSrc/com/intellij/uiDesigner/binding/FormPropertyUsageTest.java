// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.Collection;


public class FormPropertyUsageTest extends JavaPsiTestCase {
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
    doPropertyUsageTest("test.properties", 960);
  }

  public void testFormPropertyUsageForBundleInPackage() {
    doPropertyUsageTest("messages/test.properties", 876);
  }

  public void testLocalizedPropertyUsage() {
    doPropertyUsageTest("test_ru.properties", 960);
  }

  private void doPropertyUsageTest(final String propertyFileName, int offset) {
    PropertiesFile propFile = (PropertiesFile) myPsiManager.findFile(myTestProjectRoot.findFileByRelativePath(propertyFileName));
    assertNotNull(propFile);
    final Property prop = (Property)propFile.findPropertyByKey("key");
    assertNotNull(prop);
    final Query<PsiReference> query = ReferencesSearch.search(prop);
    final Collection<PsiReference> result = query.findAll();
    assertEquals(1, result.size());
    PsiReference reference = ContainerUtil.getFirstItem(result);
    assertTrue(reference.isReferenceTo(prop));
    PsiElement resolved = reference.resolve();
    assertNotNull(resolved);
    assertTrue(prop.isEquivalentTo(resolved));
    verifyReference(reference, "form.form", offset);
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
    verifyReference(ContainerUtil.getFirstItem(result), "form.form", 949);
  }

  private static void verifyReference(final PsiReference ref, final String fileName, final int offset) {
    final PsiElement element = ref.getElement();
    assertEquals(fileName, element.getContainingFile().getName());
    int startOffset = element.getTextOffset() + ref.getRangeInElement().getStartOffset();
    assertEquals(offset, startOffset);
  }

}
