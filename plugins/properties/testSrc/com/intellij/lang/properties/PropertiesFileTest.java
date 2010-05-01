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
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author max
 */
public class PropertiesFileTest extends LightPlatformTestCase {
  private Property myPropertyToAdd;

  public PropertiesFileTest() {
    PlatformTestCase.initPlatformLangPrefix();    
  }

  protected void setUp() throws Exception {
    super.setUp();
    myPropertyToAdd = PropertiesElementFactory.createProperty(getProject(), "kkk", "vvv");
  }

  public void testAddPropertyAfterComment() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "#xxxxx");
    propertiesFile.addProperty(myPropertyToAdd);

    List<Property> properties = propertiesFile.getProperties();
    Property added = properties.get(0);
    assertPropertyEquals(added, myPropertyToAdd.getName(), myPropertyToAdd.getValue());
  }

  private static void assertPropertyEquals(final Property property, @NonNls String name, @NonNls String value) {
    assertEquals(name, property.getName());
    assertEquals(value, property.getValue());
  }

  public void testAddPropertyAfterProperty() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy");
    propertiesFile.addProperty(myPropertyToAdd);

    List<Property> properties = propertiesFile.getProperties();
    assertEquals(2, properties.size());
    assertPropertyEquals(properties.get(0), "xxx", "yyy");
    assertPropertyEquals(properties.get(1), myPropertyToAdd.getName(), myPropertyToAdd.getValue());
  }
  public void testDeleteProperty() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\n#s\nzzz=ttt\n\n");

    List<Property> properties = propertiesFile.getProperties();
    assertEquals(2, properties.size());
    assertPropertyEquals(properties.get(0), "xxx", "yyy");
    assertPropertyEquals(properties.get(1), "zzz", "ttt");

    properties.get(1).delete();
    List<Property> propertiesAfter = propertiesFile.getProperties();
    assertEquals(1, propertiesAfter.size());
    assertPropertyEquals(propertiesAfter.get(0), "xxx", "yyy");
  }

  public void testDeletePropertyWhitespaceAround() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\nxxx2=tyrt\nxxx3=ttt\n\n");

    Property property = propertiesFile.findPropertyByKey("xxx2");
    property.delete();

    assertEquals("xxx=yyy\nxxx3=ttt\n\n", propertiesFile.getText());
  }
  public void testDeletePropertyWhitespaceAhead() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\nxxx2=tyrt\nxxx3=ttt\n\n");

    Property property = propertiesFile.findPropertyByKey("xxx");
    property.delete();

    assertEquals("xxx2=tyrt\nxxx3=ttt\n\n", propertiesFile.getText());
  }

  public void testAddToEnd() throws IncorrectOperationException {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\\nccc");
    assertEquals(1,propertiesFile.getProperties().size());
    propertiesFile.addProperty(myPropertyToAdd);
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
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\nc=d\ne=f");
    Property c = propertiesFile.findPropertyByKey("c");
    propertiesFile.addPropertyAfter(myPropertyToAdd, c);
    assertEquals("a=b\nc=d\nkkk=vvv\ne=f", propertiesFile.getText());
  }
  public void testAddPropertyAfterLast() throws IncorrectOperationException {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\nc=d\ne=f");
    Property p = propertiesFile.findPropertyByKey("e");
    propertiesFile.addPropertyAfter(myPropertyToAdd, p);
    assertEquals("a=b\nc=d\ne=f\nkkk=vvv", propertiesFile.getText());
  }
  public void testAddPropertyAfterInBeginning() throws IncorrectOperationException {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\nc=d\ne=f");
    propertiesFile.addPropertyAfter(myPropertyToAdd, null);
    assertEquals("kkk=vvv\na=b\nc=d\ne=f", propertiesFile.getText());
  }
  public void testUnescapedKey() throws IncorrectOperationException {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a\\:b=xxx\nc\\ d=xxx\n\\ e\\=f=xxx\n\\u1234\\uxyzt=xxxx");
    List<Property> properties = propertiesFile.getProperties();
    assertEquals("a:b", properties.get(0).getUnescapedKey());
    assertEquals("c d", properties.get(1).getUnescapedKey());
    assertEquals(" e=f", properties.get(2).getUnescapedKey());
    assertEquals("\u1234\\uxyzt", properties.get(3).getUnescapedKey());
  }
}
