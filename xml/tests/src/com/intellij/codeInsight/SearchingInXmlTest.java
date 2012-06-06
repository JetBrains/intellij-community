package com.intellij.codeInsight;

import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;

/**
 * author: lesya
 */
public class SearchingInXmlTest extends LightIdeaTestCase {
  public void test() throws IncorrectOperationException {
    XmlTag xmlTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<root>" +
                                                     "<tag1><name>name1</name><value>1</value></tag1>" +
                                                     "<tag1><name>name2</name><value>2</value></tag1>" +
                                                     "<tag1><name>name3</name><value>3</value></tag1>" +
                                                     "<tag1><name>name4</name><value>4</value></tag1>" +
                                                     "<tag1><name>name5</name><value>5</value></tag1>" +
                                                     "<tag1><name>name6</name><value>6</value></tag1>" +
                                                     "<tag1><name>name7</name><value>7</value></tag1>" +
                                                     "</root>");
    XmlTag found = XmlUtil.find("name", "name4", "tag1", xmlTag);

    assertEquals("4", found.findFirstSubTag("value").getValue().getText());
  }
}
