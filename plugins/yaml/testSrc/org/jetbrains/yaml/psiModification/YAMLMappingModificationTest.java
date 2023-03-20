// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psiModification;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

public class YAMLMappingModificationTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/psiModification/data/";
  }

  // TODO make

  public void testCreateKeyInEmptyFile() {
    doMappingTest("key", "value");
  }
  
  public void testCreateSecondTopLevelKey() {
    doMappingTest("key2", "value2");
  }

  public void testCreateSecondTopLevelKeyWithTailLines() {
    doMappingTest("key2", "value2");
  }

  public void testReplaceTopLevelKey() {
    doMappingTest("key", "value2");
  }
  
  public void testCreateSecondKeyLevelTwo() {
    doMappingTest("key", "value");
  }

  public void testCreateSecondKeyLevelTwoWithTailLines() {
    doMappingTest("key", "value");
  }

  public void testCreateSecondKeyLevelTwoWithComment() {
    doMappingTest("key", "value");
  }

  public void testCreateSecondKeyLevelTwoCompact() {
    doMappingTest("key", "value");
  }

  public void testDeleteKeyInEmptyFile() {
    doDeleteTest();
  }

  public void testDeleteKeyInFirstLine() {
    doDeleteTest();
  }

  public void testDeleteKeyInInvalidSyntax() {
    doDeleteTest();
  }

  public void testDeleteKeyInLastLine() {
    doDeleteTest();
  }

  public void testDeleteKeyInNestedMapping() {
    doDeleteTest();
  }

  public void testDeleteKeyInNestedMappingWithComment() {
    doDeleteTest();
  }

  public void testDeleteKeyWithCommentOnNextLine() {
    doDeleteTest();
  }

  public void testDeleteKeyWithCommentOnPreviousLine() {
    doDeleteTest();
  }

  public void testDeleteKeyWithCommentOnSameLine() {
    doDeleteTest();
  }

  public void testReplaceValueScalarScalar() {
    doValueTest("newValue");
  }
  
  public void testReplaceValueCompoundScalar() {
    doValueTest("newValue");
  }
  
  public void testReplaceValueScalarCompound() {
    doValueTest("""
                  someKey:
                  - bla
                  - bla""");
  }
  
  public void testSetValueScalar() {
    doValueTest("newValue");
  }
  
  public void testSetValueCompound() {
    doValueTest("""
                  someKey:
                  - bla
                  - bla""");
  }
  
  private void doValueTest(final String valueText) {
    myFixture.configureByFile(getTestName(true) + ".yml");
    final int offset = myFixture.getCaretOffset();
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
    final YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(elementAtCaret, YAMLKeyValue.class, false);
    assertNotNull(keyValue);

    final YAMLKeyValue dummyKV = YAMLElementGenerator.getInstance(myFixture.getProject()).createYamlKeyValue("foo", valueText);
    assertNotNull(dummyKV.getValue());
    
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> keyValue.setValue(dummyKV.getValue()));

    assertSameLinesWithFile(getTestDataPath() + getTestName(true) + ".txt", myFixture.getFile().getText(), false);
  }

  private void doMappingTest(final String key, final String content) {
    myFixture.configureByFile(getTestName(true) + ".yml");

    final int offset = myFixture.getCaretOffset();
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
    final YAMLMapping mapping = PsiTreeUtil.getParentOfType(elementAtCaret, YAMLMapping.class, false);
    
    if (mapping == null) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        YAMLUtil.createI18nRecord((YAMLFile)myFixture.getFile(), new String[]{key}, content);
      });
    }
    else {
      assertNotNull(mapping);

      //final int indent = YAMLUtil.getIndentInThisLine(mapping);
      //final String indentString = StringUtil.repeatSymbol(' ', indent);

      final YAMLKeyValue dummyKeyValue = YAMLElementGenerator.getInstance(myFixture.getProject()).createYamlKeyValue(key, content);
      assertNotNull(dummyKeyValue);

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> mapping.putKeyValue(dummyKeyValue));
    }

    assertSameLinesWithFile(getTestDataPath() + getTestName(true) + ".txt", myFixture.getFile().getText(), false);
  }

  private void doDeleteTest() {
    myFixture.configureByFile(getTestName(true) + ".yml");

    final PsiElement element = myFixture.getElementAtCaret();
    final YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> keyValue.getParentMapping().deleteKeyValue(keyValue));

    assertSameLinesWithFile(getTestDataPath() + getTestName(true) + ".txt", myFixture.getFile().getText());
  }
}
