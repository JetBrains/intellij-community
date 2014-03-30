/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * @author peter
 */
public class DomSaxParserTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testGetRootTagNameWithoutNamespace() throws Throwable {
    assertData("<root>", "root", null, null, null);
  }

  public void testGetRootTagNameWithNamespaceWithEmptyPrefix() throws Throwable {
    assertData("<root xmlns=\"foo\">", "root", "foo", null, null);
  }

  public void testGetRootTagNameWithUnfinishedAttribute() throws Throwable {
    XmlFile file = createXmlFile("<root xmlns=\"foo\" aaa>");
    ensureParsed(file);
    final XmlFileHeader header = DomService.getInstance().getXmlFileHeader(file);
    assertEquals(new XmlFileHeader("root", "foo", null, null), header);
  }

  public void testGetRootTagNameWithNamespaceWithNonEmptyPrefix() throws Throwable {
    assertData("<bar:root xmlns=\"foo\" xmlns:bar=\"b\">", "root", "b", null, null);
  }

  public void testGetRootTagNameWithDtdNamespace() throws Throwable {
    assertData("<!DOCTYPE ejb-jar PUBLIC\n" +
               "\"-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN\"\n" +
               "\"http://java.sun.com/dtd/ejb-jar_2_0.dtd\"><root>", "root", null, "-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN", "http://java.sun.com/dtd/ejb-jar_2_0.dtd");
  }

  public void testGetRootTagNameWithDtdNamespace2() throws Throwable {
    assertData("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<!DOCTYPE ejb-jar PUBLIC\n" +
               "\"-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN\"\n" +
               "\"http://java.sun.com/dtd/ejb-jar_2_0.dtd\"><root>", "root", null, "-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN", "http://java.sun.com/dtd/ejb-jar_2_0.dtd");
  }

  public void testNoTag() throws Throwable {
    assertData("aaaaaaaaaaaaaaaaaaaaa", null, null, null, null);
  }

  public void testEmptyFile() throws Throwable {
    assertData("", null, null, null, null);
  }

  public void testInvalidContent() throws Throwable {
    assertData("<?xmlmas8v6708986><OKHD POH:&*$%*&*I8yo9", null, null, null, null);
  }

  public void testInvalidContent2() throws Throwable {
    assertData("?xmlmas8v6708986><OKHD POH:&*$%*&*I8yo9", null, null, null, null);
  }

  private static PsiElement ensureParsed(PsiFile file) {
    return file.findElementAt(2);
  }

  public void testInvalidContent3() throws Throwable {
    assertData("<?xmlmas8v67089", null, null, null, null);
  }

  public void testSubtag() throws Throwable {
    assertData("<root><foo/>", "root", null, null, null);
  }

  public void testSpring() throws Throwable {
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

  public void testInternalDtd() throws Throwable {
    assertData("<?xml version=\"1.0\"?>\n" +
               "<!DOCTYPE \n" + 
               "        hibernate-mapping SYSTEM\n" +
               "\t\t\t\"http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd\"\n" + "[\n" +
               "<!ENTITY % globals SYSTEM \"classpath://auction/persistence/globals.dtd\">\n" + "%globals;\n" + "]><a/>", "a", null, null, "http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd");
  }

  private void assertData(final String start, @Nullable final String localName, @Nullable String namespace, @Nullable String publicId, @Nullable String systemId) throws IOException, SAXException {
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
