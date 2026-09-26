// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.navigation;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.Set;
import java.util.stream.Collectors;

public class GotoRelatedPropertyDeclarationsProviderTest extends BasePlatformTestCase {

  public void testReturnsPropertyInOtherLocales() {
    myFixture.addFileToProject("p_en.properties", "key=en");
    myFixture.addFileToProject("p_fr.properties", "key=fr");
    PsiFile file = myFixture.configureByText("p.properties", "ke<caret>y=value");

    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file));
    assertEquals(2, items.size());
    Set<String> fileNames = items.stream().map(i -> i.getElement().getContainingFile().getName()).collect(Collectors.toSet());
    assertEquals(Set.of("p_en.properties", "p_fr.properties"), fileNames);
  }

  public void testFallsBackToFileWhenKeyMissing() {
    myFixture.addFileToProject("p_en.properties", "key=en");
    myFixture.addFileToProject("p_fr.properties", "other=fr");
    PsiFile file = myFixture.configureByText("p.properties", "ke<caret>y=value");

    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file));
    assertEquals(2, items.size());
    // p_en has the key — navigates to the property element (inside the file)
    var enItem = ContainerUtil.find(items, i -> i.getElement().getContainingFile().getName().equals("p_en.properties"));
    assertNotNull(enItem);
    assertFalse(enItem.getElement() instanceof PsiFile);
    // p_fr doesn't have the key — navigates to the file itself
    var frItem = ContainerUtil.find(items, i -> i.getElement().getContainingFile().getName().equals("p_fr.properties"));
    assertNotNull(frItem);
    assertInstanceOf(frItem.getElement(), PsiFile.class);
  }

  public void testExcludesCurrentFile() {
    myFixture.addFileToProject("p_en.properties", "key=en");
    PsiFile file = myFixture.configureByText("p.properties", "ke<caret>y=value");

    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file));
    assertEquals(1, items.size());
    assertFalse(ContainerUtil.exists(items, i -> i.getElement().getContainingFile().getName().equals("p.properties")));
  }

  public void testFallsBackToFilesWhenNotOnProperty() {
    myFixture.addFileToProject("p_en.properties", "key=en");
    myFixture.addFileToProject("p_fr.properties", "other=fr");
    PsiFile file = myFixture.configureByText("p.properties", "<caret>\nkey=value");

    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file));
    assertEquals(2, items.size());
    assertTrue(ContainerUtil.and(items, i -> i.getElement() instanceof PsiFile));
  }

  public void testWorksWhenCaretOnValue() {
    myFixture.addFileToProject("p_en.properties", "key=en");
    PsiFile file = myFixture.configureByText("p.properties", "key=val<caret>ue");

    // Pass the leaf element at caret (inside value), not the Property node
    PsiElement leaf = file.findElementAt(myFixture.getCaretOffset());
    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file, leaf));
    assertEquals(1, items.size());
    assertFalse(items.getFirst().getElement() instanceof PsiFile);
  }

  public void testWorksWhenCaretOnValueWithoutPsiElement() {
    myFixture.addFileToProject("p_en.properties", "key=en");
    PsiFile file = myFixture.configureByText("p.properties", "key=val<caret>ue");

    // Simulate real scenario: PSI_ELEMENT is null (no reference at value), but editor caret is on the value
    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file, null));
    assertEquals(1, items.size());
    assertFalse(items.getFirst().getElement() instanceof PsiFile);
  }

  public void testWorksWhenCaretAtEndOfValue() {
    myFixture.addFileToProject("p_en.properties", "key=en");
    PsiFile file = myFixture.configureByText("p.properties", "key=value<caret>");

    // Caret is right after the last char of the value, PSI_ELEMENT may be null
    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file, null));
    assertEquals(1, items.size());
    assertFalse(items.getFirst().getElement() instanceof PsiFile);
  }

  public void testEmptyForSingleFile() {
    PsiFile file = myFixture.configureByText("p.properties", "key=value");
    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file));
    assertTrue(items.isEmpty());
  }

  public void testResolvesEscapedKeyAcrossLocales() {
    myFixture.addFileToProject("p_en.properties", "foo\\ bar=en");
    PsiFile file = myFixture.configureByText("p.properties", "foo\\ b<caret>ar=value");

    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file));
    assertEquals(1, items.size());
    var enItem = items.getFirst();
    assertEquals("p_en.properties", enItem.getElement().getContainingFile().getName());
    // Key matches — should navigate to the property, not fall back to the file
    assertFalse(enItem.getElement() instanceof PsiFile);
  }

  public void testResolvesUnicodeEscapedKeyAcrossLocales() {
    myFixture.addFileToProject("p_en.properties", "A=en");
    PsiFile file = myFixture.configureByText("p.properties", "\\u004<caret>1=value");

    var items = new GotoRelatedPropertyDeclarationsProvider().getItems(dataContext(file));
    assertEquals(1, items.size());
    var enItem = items.getFirst();
    assertEquals("p_en.properties", enItem.getElement().getContainingFile().getName());
    assertFalse(enItem.getElement() instanceof PsiFile);
  }

  private DataContext dataContext(PsiFile file) {
    PsiElement property = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), Property.class);
    return dataContext(file, property);
  }

  private DataContext dataContext(PsiFile file, PsiElement element) {
    var builder = SimpleDataContext.builder()
      .add(CommonDataKeys.PSI_FILE, file)
      .add(CommonDataKeys.PROJECT, file.getProject())
      .add(CommonDataKeys.EDITOR, myFixture.getEditor());
    if (element != null) {
      builder.add(CommonDataKeys.PSI_ELEMENT, element);
    }
    return builder.build();
  }

}
