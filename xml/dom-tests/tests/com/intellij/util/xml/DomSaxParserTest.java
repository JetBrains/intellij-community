/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomSaxParserTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testGetRootTagNameWithoutNamespace() {
    assertData("<root>", "root", null, null, null);
  }

  public void testGetRootTagNameWithNamespaceWithEmptyPrefix() {
    assertData("<root xmlns=\"foo\">", "root", "foo", null, null);
  }

  public void testGetRootTagNameWithUnfinishedAttribute() {
    XmlFile file = createXmlFile("<root xmlns=\"foo\" aaa>");
    ensureParsed(file);
    final XmlFileHeader header = DomService.getInstance().getXmlFileHeader(file);
    assertEquals(new XmlFileHeader("root", "foo", null, null), header);
  }

  public void testGetRootTagNameWithNamespaceWithNonEmptyPrefix() {
    assertData("<bar:root xmlns=\"foo\" xmlns:bar=\"b\">", "root", "b", null, null);
  }

  public void testGetRootTagNameWithDtdNamespace() {
    assertData("<!DOCTYPE ejb-jar PUBLIC\n" +
               "\"-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN\"\n" +
               "\"http://java.sun.com/dtd/ejb-jar_2_0.dtd\"><root>", "root", null, "-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN", "http://java.sun.com/dtd/ejb-jar_2_0.dtd");
  }

  public void testGetRootTagNameWithDtdNamespace2() {
    assertData("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<!DOCTYPE ejb-jar PUBLIC\n" +
               "\"-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN\"\n" +
               "\"http://java.sun.com/dtd/ejb-jar_2_0.dtd\"><root>", "root", null, "-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN", "http://java.sun.com/dtd/ejb-jar_2_0.dtd");
  }

  public void testNoTag() {
    assertData("aaaaaaaaaaaaaaaaaaaaa", null, null, null, null);
  }

  public void testEmptyFile() {
    assertData("", null, null, null, null);
  }

  public void testInvalidContent() {
    assertData("<?xmlmas8v6708986><OKHD POH:&*$%*&*I8yo9", null, null, null, null);
  }

  public void testInvalidContent2() {
    assertData("?xmlmas8v6708986><OKHD POH:&*$%*&*I8yo9", null, null, null, null);
  }

  private static void ensureParsed(PsiFile file) {
    //noinspection ResultOfMethodCallIgnored
    file.getNode().getFirstChildNode();
  }

  public void testInvalidContent3() {
    assertData("<?xmlmas8v67089", null, null, null, null);
  }

  public void testSubtag() {
    assertData("<root><foo/>", "root", null, null, null);
  }

  public void testSpring() {
    assertData("<?xml version=\"1.0\" encoding=\"gbk\"?>\n" + "\n" + "\n" +
               "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
               "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
               "       xmlns:aop=\"http://www.springframework.org/schema/aop\"\n" +
               "       xmlns:tx=\"http://www.springframework.org/schema/tx\"\n" +
               "       xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.0.xsd\n" +
               "           http://www.springframework.org/schema/aop http://www.springframework.org/schema/aop/spring-aop-2.0.xsd\n" +
               "           http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.0.xsd\">\n" +
               "</beans>", "beans", "http://www.springframework.org/schema/beans", null, null);
  }

  public void testInternalDtd() {
    assertData("<?xml version=\"1.0\"?>\n" +
               "<!DOCTYPE \n" + 
               "        hibernate-mapping SYSTEM\n" +
               "\t\t\t\"http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd\"\n" + "[\n" +
               "<!ENTITY % globals SYSTEM \"classpath://auction/persistence/globals.dtd\">\n" + "%globals;\n" + "]><a/>", "a", null, null, "http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd");
  }

  private void assertData(final String start, @Nullable final String localName, @Nullable String namespace, @Nullable String publicId, @Nullable String systemId) {
    XmlFileHeader expected = new XmlFileHeader(localName, namespace, publicId, systemId);

    XmlFile file = createXmlFile(start);
    assert !file.getNode().isParsed();
    assertEquals(expected, DomService.getInstance().getXmlFileHeader(file));

    ensureParsed(file);
    assert file.getNode().isParsed();
    assertEquals(expected, DomService.getInstance().getXmlFileHeader(file));
  }

  private XmlFile createXmlFile(String text) {
    return (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.xml", XMLLanguage.INSTANCE, text, false, false, false);
  }
}
