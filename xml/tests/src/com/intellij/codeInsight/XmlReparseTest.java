/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.AbstractReparseTestCase;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

public class XmlReparseTest extends AbstractReparseTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setFileType(StdFileTypes.XML);
  }

  public void test1() {
    String s1 = "<a>";
    String s2 = "</a>";

    prepareFile(s1, s2);
    final String beforeReparse = DebugUtil.treeToString(((XmlFileImpl)myDummyFile).getTreeElement(), true);
    insert("");
    assertEquals("Tree changed after empty reparse", beforeReparse, DebugUtil.treeToString(((XmlFileImpl)myDummyFile).getTreeElement(), true));
  }

  public void testTagData1() {
    String s1 = "<a>";
    String s2 = "</a>";

    prepareFile(s1, s2);
    insert("x");
    insert(" ");
    insert("xxxxx");
    insert("\n");
    insert("xxxxx");
  }

  public void testTagData2() {
    String s1 = "<a><b>\nSomeDataHere";
    String s2 = "\n</b></a>";

    prepareFile(s1, s2);

    PsiElement element1 = ((XmlFile)myDummyFile).getDocument().getRootTag();

    insert("x");
    insert(" ");
    insert("xxxxx");
    insert("\n");
    insert("xxxxx");

    assertSame(element1, ((XmlFile)myDummyFile).getDocument().getRootTag());
  }

  public void testTagInTag1() {
    String s1 = "<a><b>";
    String s2 = "</b></a>";

    prepareFile(s1, s2);

    insert("<");
    insert("c");
    insert("/");
    insert(">");
  }

  public void testTagInTag2() {
    String s1 = "<a><b>";
    String s2 = "</b></a>";

    prepareFile(s1, s2);

    insert("<");
    insert("c");
    insert(">");
    insert("\nxxx \nxxxx\n");
    insert("<");
    insert("/");
    insert("c");
    insert(">");
  }

  public void testTagInTag3() {
    String s1 = "<a><b>";
    String s2 = "</b></a>";

    prepareFile(s1, s2);

    insert("<");
    insert("/");
    insert("b");
    insert(">");
  }

  public void testSCR5925() {
    String s1 = "<one>     <two ";
    String s2 = ",b\"/></one>";

    prepareFile(s1, s2);
    insert("a");
    insert("t");
    insert("t");
    insert("r");
    insert("=");
    insert("\"");
  }

  public void testXmlReparseProblem() throws IncorrectOperationException {
    prepareFile("<table>\n" +
                "    <tr>\n" +
                "<td>\n" +
                "<table width"," </td>\n" +
               "    </tr>\n" +
               "</table>");
    insert("=");
  }
  private static final String marker = "<marker>";
  public void testXmlDeclDtd() throws Exception {
    PsiFile file = myFixture.addFileToProject("x.xml", "<!DOCTYPE name [\n" +
                                       "  <!ELEMENT name (" + marker+ "a b c d" + marker+ ")>\n" +
                                       "  <!ELEMENT name2 (" + marker+ "%entity;" + marker+ ")>\n" +
                                       "]>\n" +
                                       "<name></name>");

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(file);

    removeGarbage(document);

    documentManager.commitAllDocuments();
    String myFullDataPath = getTestDataPath() + "psi/";
    ParsingTestCase.doCheckResult(myFullDataPath, file, true, "testXmlDeclDtd", false, false);
  }

  private void removeGarbage(Document document) {
    int i = document.getText().indexOf(marker);
    if (i==-1) return;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(i, i + marker.length(), ""));

    removeGarbage(document);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  public void testXml() {
    setFileType(StdFileTypes.XML);
    String text2 = "</root>";
    final String text1 = "<root>/n";
    prepareFile(text1, text2);
    insert("<");
    PsiElement element = myDummyFile.findElementAt(10);
    assert element != null;
    assertNotNull(element.getTextRange());
  }

}
