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

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ReparseFixture;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.psi.ReparseFixtureKt.reparseFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class XmlReparseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<ReparseFixture> reparseFixture = reparseFixture(projectFixture, XmlFileType.INSTANCE);

  @Test
  public void test1() {
    ReparseFixture reparse = reparseFixture.get();
    String s1 = "<a>";
    String s2 = "</a>";

    reparse.prepareFile(s1, s2);
    final String beforeReparse = ReadAction.computeBlocking(() -> DebugUtil.treeToString(((XmlFileImpl)reparse.getDummyFile()).getTreeElement(), false));
    reparse.insert("");
    ReadAction.runBlocking(() -> {
      assertEquals(beforeReparse, DebugUtil.treeToString(((XmlFileImpl)reparse.getDummyFile()).getTreeElement(),
                                                         false), "Tree changed after empty reparse");
    });
  }

  @Test
  public void testTagData1() {
    ReparseFixture reparse = reparseFixture.get();
    String s1 = "<a>";
    String s2 = "</a>";

    reparse.prepareFile(s1, s2);
    reparse.insert("x");
    reparse.insert(" ");
    reparse.insert("xxxxx");
    reparse.insert("\n");
    reparse.insert("xxxxx");
  }

  @Test
  public void testTagData2() {
    ReparseFixture reparse = reparseFixture.get();
    String s1 = "<a><b>\nSomeDataHere";
    String s2 = "\n</b></a>";

    reparse.prepareFile(s1, s2);

    PsiElement element1 = ReadAction.computeBlocking(() -> ((XmlFile)reparse.getDummyFile()).getDocument().getRootTag());

    reparse.insert("x");
    reparse.insert(" ");
    reparse.insert("xxxxx");
    reparse.insert("\n");
    reparse.insert("xxxxx");

    ReadAction.runBlocking(() -> {
      assertSame(element1, ((XmlFile)reparse.getDummyFile()).getDocument().getRootTag());
    });
  }

  @Test
  public void testTagInTag1() {
    ReparseFixture reparse = reparseFixture.get();
    String s1 = "<a><b>";
    String s2 = "</b></a>";

    reparse.prepareFile(s1, s2);

    reparse.insert("<");
    reparse.insert("c");
    reparse.insert("/");
    reparse.insert(">");
  }

  @Test
  public void testTagInTag2() {
    ReparseFixture reparse = reparseFixture.get();
    String s1 = "<a><b>";
    String s2 = "</b></a>";

    reparse.prepareFile(s1, s2);

    reparse.insert("<");
    reparse.insert("c");
    reparse.insert(">");
    reparse.insert("\nxxx \nxxxx\n");
    reparse.insert("<");
    reparse.insert("/");
    reparse.insert("c");
    reparse.insert(">");
  }

  @Test
  public void testTagInTag3() {
    ReparseFixture reparse = reparseFixture.get();
    String s1 = "<a><b>";
    String s2 = "</b></a>";

    reparse.prepareFile(s1, s2);

    reparse.insert("<");
    reparse.insert("/");
    reparse.insert("b");
    reparse.insert(">");
  }

  @Test
  public void testSCR5925() {
    ReparseFixture reparse = reparseFixture.get();
    String s1 = "<one>     <two ";
    String s2 = ",b\"/></one>";

    reparse.prepareFile(s1, s2);
    reparse.insert("a");
    reparse.insert("t");
    reparse.insert("t");
    reparse.insert("r");
    reparse.insert("=");
    reparse.insert("\"");
  }

  @Test
  public void testXmlReparseProblem() throws IncorrectOperationException {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile("""
                  <table>
                      <tr>
                  <td>
                  <table width""", """
                   </td>
                      </tr>
                  </table>""");
    reparse.insert("=");
  }
  private static final String marker = "<marker>";
  @Test
  public void testXmlDeclDtd() throws Exception {
    ReparseFixture reparse = reparseFixture.get();
    PsiFile file = reparse.getFixture().addFileToProject("x.xml", "<!DOCTYPE name [\n" +
                                       "  <!ELEMENT name (" + marker+ "a b c d" + marker+ ")>\n" +
                                       "  <!ELEMENT name2 (" + marker+ "%entity;" + marker+ ")>\n" +
                                       "]>\n" +
                                       "<name></name>");

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(reparse.getProject());
    Document document = ReadAction.computeBlocking(() -> documentManager.getDocument(file));

    removeGarbage(reparse, document);

    EdtTestUtil.runInEdtAndWait(() -> documentManager.commitAllDocuments());
    String myFullDataPath = getTestDataPath() + "psi/xml";
    ReadAction.runBlocking(() -> ParsingTestFixture.doCheckResult(myFullDataPath, file, true, "testXmlDeclDtd", false, false));
  }

  private static void removeGarbage(ReparseFixture reparse, Document document) {
    int i = ReadAction.computeBlocking(() -> document.getText()).indexOf(marker);
    if (i==-1) return;
    WriteCommandAction.runWriteCommandAction(reparse.getProject(), () -> document.replaceString(i, i + marker.length(), ""));

    removeGarbage(reparse, document);
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  @Test
  public void testXml() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.setFileType(XmlFileType.INSTANCE);
    String text2 = "</root>";
    final String text1 = "<root>/n";
    reparse.prepareFile(text1, text2);
    reparse.insert("<");
    ReadAction.runBlocking(() -> {
      PsiElement element = reparse.getDummyFile().findElementAt(10);
      assert element != null;
      assertNotNull(element.getTextRange());
    });
  }

  @Test
  public void testNoExceptionsDummyIdentifierMovementWithSpace() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.setFileType(XmlFileType.INSTANCE);
    String text1 = "<rf oot><xc></root>";
    String text2 = "<root><f xc></root>"; // moved "f " to another place
    reparse.prepareFile(text1, "");
    Document document = ReadAction.computeBlocking(() -> reparse.getDummyFile().getViewProvider().getDocument());
    WriteCommandAction.runWriteCommandAction(reparse.getProject(), () -> {
      ((XmlFile)reparse.getDummyFile()).getRootTag().replace(XmlElementFactory.getInstance(reparse.getProject()).createTagFromText(text2));
    });
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkFileStructure(reparse.getDummyFile());
      assertEquals(text2, reparse.getDummyFile().getText());
      assertEquals(text2, document.getText());
    });
  }

}
