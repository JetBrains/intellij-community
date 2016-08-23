/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author max
 */
public class PropertiesFileTest extends LightPlatformCodeInsightFixtureTestCase {
  private Property myPropertyToAdd;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPropertyToAdd = (Property)PropertiesElementFactory.createProperty(getProject(), "kkk", "vvv", null);
  }

  public void testAddPropertyAfterComment() throws Exception {
    final PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "#xxxxx");
    ApplicationManager.getApplication().runWriteAction(() -> {
      propertiesFile.addProperty(myPropertyToAdd);
    });


    List<IProperty> properties = propertiesFile.getProperties();
    IProperty added = properties.get(0);
    assertPropertyEquals(added, myPropertyToAdd.getName(), myPropertyToAdd.getValue());
  }

  private static void assertPropertyEquals(final IProperty property, @NonNls String name, @NonNls String value) {
    assertEquals(name, property.getName());
    assertEquals(value, property.getValue());
  }

  public void testAddPropertyAfterProperty() throws Exception {
    final PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      propertiesFile.addProperty(myPropertyToAdd);
    });


    List<IProperty> properties = propertiesFile.getProperties();
    assertEquals(2, properties.size());
    assertPropertyEquals(properties.get(1), "xxx", "yyy");
    assertPropertyEquals(properties.get(0), myPropertyToAdd.getName(), myPropertyToAdd.getValue());
  }
  public void testDeleteProperty() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\n#s\nzzz=ttt\n\n");

    final List<IProperty> properties = propertiesFile.getProperties();
    assertEquals(2, properties.size());
    assertPropertyEquals(properties.get(0), "xxx", "yyy");
    assertPropertyEquals(properties.get(1), "zzz", "ttt");

    WriteCommandAction.runWriteCommandAction(null, () -> properties.get(1).getPsiElement().delete());

    List<IProperty> propertiesAfter = propertiesFile.getProperties();
    assertEquals(1, propertiesAfter.size());
    assertPropertyEquals(propertiesAfter.get(0), "xxx", "yyy");
  }

  public void testDeletePropertyWhitespaceAround() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\nxxx2=tyrt\nxxx3=ttt\n\n");

    final Property property = (Property)propertiesFile.findPropertyByKey("xxx2");
    WriteCommandAction.runWriteCommandAction(null, property::delete);


    assertEquals("xxx=yyy\nxxx3=ttt\n\n", propertiesFile.getContainingFile().getText());
  }
  public void testDeletePropertyWhitespaceAhead() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\nxxx2=tyrt\nxxx3=ttt\n\n");

    final Property property = (Property)propertiesFile.findPropertyByKey("xxx");
    WriteCommandAction.runWriteCommandAction(null, property::delete);


    assertEquals("xxx2=tyrt\nxxx3=ttt\n\n", propertiesFile.getText());
  }

  public void testAddToEnd() throws IncorrectOperationException {
    final PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\\nccc");
    assertEquals(1,propertiesFile.getProperties().size());
    WriteCommandAction.runWriteCommandAction(null, () -> {
        propertiesFile.addProperty(myPropertyToAdd);
      });

    assertEquals("a=b\\nccc\nkkk=vvv", propertiesFile.getText());
  }

  public void testUnescapedValue() {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\\nc\\u0063c");
    assertEquals("b\nccc", propertiesFile.getProperties().get(0).getUnescapedValue());
  }

  public void testUnescapedLineBreak() {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\\\n\t  c");
    assertEquals("bc", propertiesFile.getProperties().get(0).getUnescapedValue());
  }

  public void testAddPropertyAfter() throws IncorrectOperationException {
    final PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\nc=d\ne=f");
    final Property c = (Property)propertiesFile.findPropertyByKey("c");
    WriteCommandAction.runWriteCommandAction(null, () -> {
        propertiesFile.addPropertyAfter(myPropertyToAdd, c);
      });

    assertEquals("a=b\nc=d\nkkk=vvv\ne=f", propertiesFile.getText());
  }
  public void testAddPropertyAfterLast() throws IncorrectOperationException {
    final PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\nc=d\ne=f");
    final Property p = (Property)propertiesFile.findPropertyByKey("e");
    WriteCommandAction.runWriteCommandAction(null, () -> {
        propertiesFile.addPropertyAfter(myPropertyToAdd, p);
      });

    assertEquals("a=b\nc=d\ne=f\nkkk=vvv", propertiesFile.getText());
  }
  public void testAddPropertyAfterInBeginning() throws IncorrectOperationException {
    final PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\nc=d\ne=f");
    ApplicationManager.getApplication().runWriteAction(() -> {
      propertiesFile.addPropertyAfter(myPropertyToAdd, null);
    });

    assertEquals("kkk=vvv\na=b\nc=d\ne=f", propertiesFile.getText());
  }
  public void testUnescapedKey() throws IncorrectOperationException {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a\\:b=xxx\nc\\ d=xxx\n\\ e\\=f=xxx\n\\u1234\\uxyzt=xxxx");
    List<IProperty> properties = propertiesFile.getProperties();
    assertEquals("a:b", properties.get(0).getUnescapedKey());
    assertEquals("c d", properties.get(1).getUnescapedKey());
    assertEquals(" e=f", properties.get(2).getUnescapedKey());
    assertEquals("\u1234\\uxyzt", properties.get(3).getUnescapedKey());
  }

  public void testNonDefaultKeyValueDelimiter() {
    final PropertiesCodeStyleSettings codeStyleSettings = PropertiesCodeStyleSettings.getInstance(getProject());
    codeStyleSettings.KEY_VALUE_DELIMITER_CODE = 1;
    final PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), "xxx", "yyy", null);
    final char delimiter = property.getKeyValueDelimiter();
    assertEquals(':', delimiter);
    assertEquals("xxx:yyy", property.getPsiElement().getText());
    codeStyleSettings.KEY_VALUE_DELIMITER_CODE = 0;
  }
}
