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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;

/**
 * @author peter
 */
@SuppressWarnings({"ConstantConditions", "EmptyCatchBlock"})
public class XmlTagTest extends LightCodeInsightTestCase {
  private static XmlTag createTag(String value) throws IncorrectOperationException {
    return XmlElementFactory.getInstance(getProject()).createTagFromText("<foo>" + value + "</foo>");
  }

  public void testGetTextValue() {
    assertEquals("bar", createTag("bar").getValue().getText());
  }

  public void testCharRefs() {
    String[] names = XmlTagUtil.getCharacterEntityNames();
    for (String name : names) {
      XmlTag tag = createTag("foo&" + name + ";bar");
      assertEquals("foo" + XmlTagUtil.getCharacterByEntityName(name) + "bar", tag.getValue().getTrimmedText());
    }
  }

  public void testLocalNSDeclarations() {
    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("<foo xmlns='aaa' xmlns:a='bbbb'/>");
    final Object[] nsPrefixes = ArrayUtil.toObjectArray(tag.getLocalNamespaceDeclarations().keySet());
    Arrays.sort(nsPrefixes);
    assertEquals(2, nsPrefixes.length);
    assertEquals("a",nsPrefixes[1]);
    assertEquals("",nsPrefixes[0]);
  }

  public void testCDATA() {
    XmlTag tag = createTag("foo<![CDATA[<>&'\"]]>bar");
    assertEquals("foo<>&'\"bar", tag.getValue().getTrimmedText());
  }

  public void testWhitespacesInAttributes() {
    XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a c=d>b</a>");
    assertEquals("b", tag.getValue().getText());
  }

  public void testCreateChildTag() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a xmlns=\"aNamespace\"/>");
    final XmlTag childTag = rootTag.createChildTag("b", "bNamespace", null, true);
    assertEquals("bNamespace", childTag.getNamespace());

    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag beanTag = (XmlTag)rootTag.add(childTag);
      assertEquals("bNamespace", beanTag.getNamespace());
    });
  }

  public void testDeleteTag() {
    XmlTag aTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a><b/> </a>");
    final XmlTag bTag = aTag.findFirstSubTag("b");
    WriteCommandAction.runWriteCommandAction(null, () -> bTag.delete());

    assertEquals(0, aTag.getSubTags().length);
  }

  public void testReplaceTag() {
    final XmlTag aTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a><b/></a>");
    final XmlTag bTag = aTag.findFirstSubTag("b");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      PsiElement cTag = bTag.replace(XmlElementFactory.getInstance(getProject()).createTagFromText("<c/>"));
      assertEquals(1, aTag.getSubTags().length);
      assertEquals(cTag, aTag.getSubTags()[0]);
    });
  }

  public void testAddText() {
    final XmlElementFactory elementFactory = XmlElementFactory.getInstance(getProject());
    final XmlTag aTag = elementFactory.createTagFromText("<a>1</a>");
    final XmlText displayText = elementFactory.createDisplayText("2");

    WriteCommandAction.runWriteCommandAction(null, () -> {
      final PsiElement psiElement = aTag.addAfter(displayText, aTag.getValue().getChildren()[0]);
      assertEquals(psiElement.getContainingFile(), aTag.getContainingFile());
    });
  }

  public void testWhitespaceInsideTag() {
    WriteCommandAction.runWriteCommandAction(null, () -> XmlElementFactory.getInstance(getProject()).createTagFromText("<p/>").getValue().setText("\n"));
  }

  public void testSetAttribute_ForXhtml() {
    XmlFile xhtmlFile = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("dummy.xhtml", "<html/>");
    final XmlTag rootTag = xhtmlFile.getDocument().getRootTag();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      rootTag.setAttribute("foo", "bar");
    });

    assertEquals(1, rootTag.getAttributes().length);
    assertEquals("bar", rootTag.getAttributeValue("foo"));
    assertEquals("foo", rootTag.getAttributes()[0].getName());
  }

  public void testSetAttribute() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html/>");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      rootTag.setAttribute("foo", "bar");
    });

    assertEquals(1, rootTag.getAttributes().length);
    assertEquals("bar", rootTag.getAttributeValue("foo"));
    assertEquals("foo", rootTag.getAttributes()[0].getName());
    assertEquals("<html foo=\"bar\"/>", rootTag.getText());
  }

  public void testSetAttributeWithQuotes() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html/>");
    final String value = "a \"b\" c";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      rootTag.setAttribute("foo", value);
    });

    assertEquals(1, rootTag.getAttributes().length);
    assertEquals(value, rootTag.getAttributeValue("foo"));
    assertEquals("foo", rootTag.getAttributes()[0].getName());
    assertEquals("<html foo='" + value + "'/>", rootTag.getText());
  }

  public void testSetAttributeWithQuotes2() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html/>");
    final String value = "'a \"b\" c'";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      rootTag.setAttribute("foo", value);
    });

    final String expected = value.replaceAll("\"", "&quot;");
    assertEquals(1, rootTag.getAttributes().length);
    assertEquals(expected, rootTag.getAttributeValue("foo"));
    assertEquals(value, rootTag.getAttribute("foo").getDisplayValue());
    assertEquals("foo", rootTag.getAttributes()[0].getName());
    assertEquals("<html foo=\"" + expected + "\"/>", rootTag.getText());
  }

  public void testSetAttributeUpdateText() {
    final String value = "a \"b\" c";
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html foo='" + value + "'/>");

    assertEquals(1, rootTag.getAttributes().length);
    assertEquals(value, rootTag.getAttributeValue("foo"));

    final XmlAttribute foo = rootTag.getAttribute("foo");
    final String text = foo.getValueElement().getText();
    assertEquals("'" + value + "'", text);

    ((PsiLanguageInjectionHost)foo.getValueElement()).updateText(text);
    assertEquals("<html foo='" + value + "'/>", rootTag.getText());
  }

  public void testSetAttributeWithNamespaces() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<ns:tag xmlns:ns=\"xxx\"/>");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      rootTag.setAttribute("foo", "", "bar");
    });

    assertEquals(2, rootTag.getAttributes().length);
    assertEquals("bar", rootTag.getAttributeValue("foo"));
    assertEquals("foo", rootTag.getAttributes()[1].getName());
    assertEquals("<ns:tag xmlns:ns=\"xxx\" foo=\"bar\"/>", rootTag.getText());
  }

  public void testTextEdit1() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html>aaa</html>");
    final XmlText xmlText = rootTag.getValue().getTextElements()[0];
    WriteCommandAction.runWriteCommandAction(null, () -> xmlText.removeText(0, 3));

    assertEquals("<html></html>", rootTag.getText());
  }

  public void testTextEdit2() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html>a&lt;a</html>");
    WriteCommandAction.runWriteCommandAction(null, () -> rootTag.getValue().getTextElements()[0].removeText(0, 3));

    assertEquals("<html></html>", rootTag.getText());
  }

  public void testTextEdit3() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html>a&lt;a</html>");
    final XmlText xmlText = rootTag.getValue().getTextElements()[0];
    WriteCommandAction.runWriteCommandAction(null, () -> xmlText.removeText(1, 2));

    assertEquals(1, xmlText.getChildren().length);
    assertEquals("<html>aa</html>", rootTag.getText());
  }

  public void testTextEdit4() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html>aaa</html>");
    final XmlText xmlText = rootTag.getValue().getTextElements()[0];
    WriteCommandAction.runWriteCommandAction(null, () -> xmlText.removeText(1, 2));

    assertEquals(1, xmlText.getChildren().length);
    assertEquals("<html>aa</html>", rootTag.getText());
  }

  public void testTextEdit5() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html><x>xxx</x>\n<y>yyy</y></html>");
    final XmlTag y = rootTag.findFirstSubTag("y");

    final PsiFile file = y.getContainingFile();
    String text = y.getValue().getText();
    TextRange textRange = y.getValue().getTextRange();

    assertEquals(text, textRange.substring(file.getText()));

    new WriteCommandAction(getProject(), file) {
      @Override
      protected void run(@NotNull final Result result) {
        CodeStyleManager.getInstance(getProject()).adjustLineIndent(file, y.getTextOffset());
      }
    }.execute();

    text = y.getValue().getText();
    textRange = y.getValue().getTextRange();

    assertEquals(text, textRange.substring(file.getText()));
  }

  public void testTextEdit6() {
    final XmlTag rootTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<html>a<b>1</b>c</html>");
    final XmlTag xmlTag = rootTag.findFirstSubTag("b");
    WriteCommandAction.runWriteCommandAction(null, () -> xmlTag.delete());

    assertEquals("<html>ac</html>", rootTag.getText());
  }

  public void testBrace() {
   final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a/>");
    WriteCommandAction.runWriteCommandAction(null, () -> tagFromText.getValue().setText("<"));

    assertEquals("<", tagFromText.getValue().getTextElements()[0].getValue());
  }

  public void testEmptyTextRange() {
    final String text = "<a></a>";
    final String name = "test.xhtml";
    XmlTag rootTag = createTag(name, text);
    TextRange textRange = rootTag.getValue().getTextRange();
    assertEquals(3, textRange.getStartOffset());
    assertEquals(3, textRange.getEndOffset());
  }

  public void testDeleteTagBetweenText() {
    final XmlTag tag = createTag("foo.xhtml", "<p>a<div/>b</p>");
    final XmlTag div = tag.getSubTags()[0];
    new WriteCommandAction(getProject(), tag.getContainingFile()) {
      @Override
      protected void run(@NotNull final Result result) {
        div.delete();
      }
    }.execute();
    assertEquals("<p>ab</p>", tag.getText());
  }

  private static XmlTag createTag(final String name, final String text) {
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText(name, StdFileTypes.XML, text, LocalTimeCounter.currentTime(), true);
    return file.getDocument().getRootTag();
  }

  // tests whether rangemarker gets changed when texts are merged, passes
  public void testRangeMarker1() throws IncorrectOperationException {
    final XmlFile file = (XmlFile)createFile("file.xhtml", "<a>1234<b></b>567</a>");
    final XmlTag root = file.getDocument().getRootTag();
    final XmlTag tag = root.findFirstSubTag("b");

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      final int offset = tag.getTextOffset();

      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(root.getContainingFile());
      RangeMarker marker = document.createRangeMarker(offset - 3, offset);
      tag.delete();


      assertEquals(4, marker.getStartOffset());
      assertEquals(7, marker.getEndOffset());
    }, "", null));
  }

  // this one fails, the difference is that we do some manipulations before: move "234" before the tag
  public void testRangeMarker2() throws IncorrectOperationException {
    final XmlTag root = createTag("file.xhtml", "<a>1<b>234</b>567</a>");
    final XmlTag tag = root.findFirstSubTag("b");
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(root.getContainingFile());
      XmlTagChild child = tag.getValue().getChildren()[0];
      assertTrue(child instanceof XmlText && child.getText().equals("234"));

      try {
        tag.getParent().addBefore(child, tag);

        assertEquals(7, tag.getTextOffset());
        RangeMarker marker = document.createRangeMarker(4, 7);
        tag.delete();

        assertEquals(4, marker.getStartOffset());
        assertEquals(7, marker.getEndOffset());

      }
      catch (IncorrectOperationException e) {
      }
    }, "", null));
  }

  // the previous test relveals one problem with text merge, "234" in fact is not merged with "1"
  public void test3() throws IncorrectOperationException {
    final XmlTag root = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>1<b>234</b>456</a>");
    final XmlTag tag = root.findFirstSubTag("b");

    final XmlTagChild child = tag.getValue().getChildren()[0];
    assertTrue(child instanceof XmlText && child.getText().equals("234"));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      assertNotNull(tag.getParent().addBefore(child, tag));
      tag.delete();
    });


    assertEquals(1, root.getValue().getChildren().length);
    assertEquals("1234456", root.getValue().getChildren()[0].getText());
  }

  public void test3XHTML() throws IncorrectOperationException {
    final XmlTag root = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a>1<b>234</b>456</a>");
    final XmlTag tag = root.findFirstSubTag("b");

    final XmlTagChild child = tag.getValue().getChildren()[0];
    assertTrue(child instanceof XmlText && child.getText().equals("234"));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      assertNotNull(tag.getParent().addBefore(child, tag));
      tag.delete();
    });


    assertEquals(1, root.getValue().getChildren().length);
    assertEquals("1234456", root.getValue().getChildren()[0].getText());
  }

  public void testDisplayText() {
    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("  <foo/>");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.add(XmlElementFactory.getInstance(getProject()).createDisplayText("aaa\nbbb"));
    });

    assertEquals("aaa\nbbb", tag.getValue().getTextElements()[0].getValue());
  }

  public void testXHTMLAddBefore1() {
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("test.xhtml",
                                                                                                              "<a>a <b>123</b> c</a>");
    final XmlTag tagB = file.getDocument().getRootTag().findFirstSubTag("b");

    final XmlTagChild[] tagElements = tagB.getValue().getChildren();
    final PsiElement parent = tagB.getParent();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      PsiElement first = parent.addBefore(tagElements[0], tagB);
      assertNotNull(first);
    });
  }

  public void testXHTMLSetAttribute1() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("test.xhtml", "<a/>");
        final XmlTag tagB = file.getDocument().getRootTag();

        tagB.setAttribute("a", "");
        assertEquals("<a a=\"\"/>", tagB.getText());

        tagB.setAttribute("b", "");
        assertEquals("<a a=\"\" b=\"\"/>", tagB.getText());

        tagB.setAttribute("c", "");
        assertEquals("<a a=\"\" b=\"\" c=\"\"/>", tagB.getText());

        tagB.getAttributes()[1].delete();
        assertEquals("<a a=\"\"  c=\"\"/>", tagB.getText());
      }
    }.execute().throwException();
  }

  public void testXHTMLNbsp1() {
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("test.xhtml", "<a>&nbsp;</a>");
    final XmlTag tagB = file.getDocument().getRootTag();
    assertEquals(tagB.getValue().getTextElements().length, 1);
    assertEquals(tagB.getValue().getTextElements()[0].getValue(), "\u00a0");
  }

  public void testDeleteTagWithMultilineWhitespace1() {
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("test.xml",
                                                                                                              "<a>\n  <a/>\n</a>");
    final XmlTag tagB = file.getDocument().getRootTag();
    ApplicationManager.getApplication().runWriteAction(() -> tagB.getSubTags()[0].delete());

    assertEquals("<a>\n  </a>", tagB.getText());
  }

  public void testDeleteTagWithMultilineWhitespace2() {
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText("test.xml", "<a>\n    <a>\n <b>\n     hasgdgasjdgasdg    asgdjhasgd</b>\n </a>\n</a>");
    final XmlTag tagB = file.getDocument().getRootTag();
    WriteCommandAction.runWriteCommandAction(null, () -> tagB.getSubTags()[0].getSubTags()[0].delete());

    assertEquals("<a>\n    <a>\n </a>\n</a>", tagB.getText());
  }

  public void testXHTMLRangeMarkers2() {
    XmlTag tag = createTag("file.xhtml", "<a>xyz</a>");
    PsiFile psiFile = tag.getContainingFile();
    Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    RangeMarker rangeMarker = document.createRangeMarker(5, 5);
    final XmlText text = (XmlText) tag.getValue().getChildren()[0];

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      try{
        text.removeText(2, 3);
      }
      catch(IncorrectOperationException ioe){}
    }, "", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION));

    assertEquals(5, rangeMarker.getStartOffset());
    assertEquals(5, rangeMarker.getEndOffset());
  }

  public void testXHTMLTextInsert() {
    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a>xyz</a>");
    ApplicationManager.getApplication().runWriteAction(() -> tag.getValue().getTextElements()[0].insertText("<", 1));

    assertEquals("<a>x&lt;yz</a>", tag.getText());
  }

  public void testSimpleTextInsertion() {
    doTestSimpleInsertion("xyz", "a");
    doTestSimpleInsertion(" xyz", "a");
    doTestSimpleInsertion("x yz", "a");
    doTestSimpleInsertion("xy z", "a");
    doTestSimpleInsertion("xyz ", "a");
    doTestSimpleInsertion(" xyz ", "a");
    doTestSimpleInsertion(" x y z ", "a");
  }

  public void testWhitespaceTextInsertion() {
    doTestSimpleInsertion("xyz", " ");
  }

  public void testSimpleTextDeletion() {
    doTestSimpleDeletion("xyz");
    doTestSimpleDeletion(" xyz");
    doTestSimpleDeletion("x yz");
    doTestSimpleDeletion("xy z");
    doTestSimpleDeletion("xyz ");
    doTestSimpleDeletion(" xyz ");
    doTestSimpleDeletion(" x y z ");
  }

  public void testWhitespaceDeletion() {
    doTestSimpleDeletion(" xyz");
    doTestSimpleDeletion("x yz");
    doTestSimpleDeletion("xy z");
    doTestSimpleDeletion("xyz ");
    doTestSimpleDeletion(" xyz ");
    doTestSimpleDeletion(" x y z ");
  }

  private static void doTestSimpleDeletion(final String text) throws IncorrectOperationException {
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (int i = 0; i < text.length(); i++) {
        for (int j = i; j < text.length(); j++) {
          XmlTag tag = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a>" + text + "</a>");
          final PsiElement[] children = tag.getValue().getTextElements();
          assertEquals(1, children.length);
          final XmlText xmlText = (XmlText)children[0];
          assertEquals(text, xmlText.getText());
          xmlText.removeText(i, j);
          final StringBuilder expected = new StringBuilder(text);
          expected.delete(i, j);
          assertEquals(expected.toString(), xmlText.getValue());
        }
      }
    });
  }

  private static void doTestSimpleInsertion(final String text, final String textToInsert) throws IncorrectOperationException {
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (int i = 0; i <= text.length(); i++) {
        XmlTag tag = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a>" + text + "</a>");
        final PsiElement[] children = tag.getValue().getTextElements();
        assertEquals(1, children.length);
        final XmlText xmlText = (XmlText)children[0];
        assertEquals(text, xmlText.getValue());
        xmlText.insertText(textToInsert, i);
        final StringBuilder expected = new StringBuilder(text);
        expected.insert(i, textToInsert);
        assertEquals(expected.toString(), xmlText.getValue());
      }
    });
  }

  public void testEscapedInsertion() {
    doTestEscapedInsertion("xyz", "&");
    doTestEscapedInsertion("xyz", "&&");
    doTestEscapedInsertion("xyz", "&x&");
    doTestEscapedInsertion("&xyz", "a");
    doTestEscapedInsertion("&xyz", " ");
    doTestEscapedInsertion("x&yz", "a");
    doTestEscapedInsertion("x&yz", " ");
    doTestEscapedInsertion("xy&z", "a");
    doTestEscapedInsertion("xy&z", " ");
    doTestEscapedInsertion("xyz&", "a");
    doTestEscapedInsertion("xyz&", " ");
    doTestEscapedInsertion("&x&y&z&", "a");
    doTestEscapedInsertion(" x&y&z&", "a");
    doTestEscapedInsertion(" x y&z&", "a");
    doTestEscapedInsertion("&x y&z&", "a");
    doTestEscapedInsertion("&x y&z ", "a");
    doTestEscapedInsertion("&x&y&z&", "<");
    doTestEscapedInsertion("&x&y&z&", ">");
    doTestEscapedInsertion("_xyz", "a");
    doTestEscapedInsertion("x_yz", "a");
    doTestEscapedInsertion("xy_z", "a");
    doTestEscapedInsertion("xyz_", "a");
    doTestEscapedInsertion("_xyz_", "a");
    doTestEscapedInsertion("_x_y_z_", "a");
  }

  public static void notestEscapedDeletion() {
    doTestEscapedDeletion("&");
    doTestEscapedDeletion("&&");
    doTestEscapedDeletion(" &&");
    doTestEscapedDeletion(" & &");
    doTestEscapedDeletion(" & & ");
    doTestEscapedDeletion(" && ");
    doTestEscapedDeletion("&& ");
    doTestEscapedDeletion("& ");
    doTestEscapedDeletion(" ");
    doTestEscapedDeletion("&abc");
    doTestEscapedDeletion("a&bc");
    doTestEscapedDeletion("ab&c");
    doTestEscapedDeletion("abc&");
    doTestEscapedDeletion(" &abc");
    doTestEscapedDeletion("a &bc");
    doTestEscapedDeletion("ab &c");
    doTestEscapedDeletion("abc &");
    doTestEscapedDeletion("& abc");
    doTestEscapedDeletion("a& bc");
    doTestEscapedDeletion("ab& c");
    doTestEscapedDeletion("abc& ");
  }

  private static void doTestEscapedInsertion(final String text, final String textToInsert) throws IncorrectOperationException {
    String tagText = toEscapedText(text);
    for (int i = 0; i <= text.length(); i++) {
      XmlTag tag = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a>" + tagText + "</a>");
      final PsiElement[] children = tag.getValue().getTextElements();
      assertEquals(1, children.length);
      final XmlText xmlText = (XmlText)children[0];
      assertEquals(tagText, xmlText.getText());
      xmlText.insertText(textToInsert, i);
      final StringBuilder expectedDisplay = new StringBuilder(text.replace('_', '\u00a0'));
      expectedDisplay.insert(i, textToInsert);
      assertEquals(expectedDisplay.toString(), xmlText.getValue());

//      final String expectedText = toEscapedText(expectedDisplay.toString());
//      assertEquals(expectedText, xmlText.getText());
    }
  }

  private static void doTestEscapedDeletion(final String text) throws IncorrectOperationException {
    ApplicationManager.getApplication().runWriteAction(() -> {
      String tagText = toEscapedText(text);
      for (int i = 0; i < text.length(); i++) {
        for (int j = i; j < text.length(); j++) {
          XmlTag tag = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a>" + tagText + "</a>");
          final PsiElement[] children = tag.getValue().getTextElements();
          assertEquals(1, children.length);
          final XmlText xmlText = (XmlText)children[0];
          assertEquals(tagText, xmlText.getText());
          xmlText.removeText(i, j);
          final StringBuilder expectedDisplay = new StringBuilder(text.replace('_', ' '));
          expectedDisplay.delete(i, j);
          assertEquals(expectedDisplay.toString(), xmlText.getValue());

          final String expectedText = toEscapedText(expectedDisplay.toString());
          assertEquals(expectedText, xmlText.getText());
        }
      }
    });
  }

  public void testWhitespacesInEmptyXHTMLTag() {
    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a> <b/> </a>");
    ApplicationManager.getApplication().runWriteAction(() -> tag.findFirstSubTag("b").delete());

    assertEquals("<a>  </a>", tag.getText());
  }

  public void test2() {
    XmlFile file = (XmlFile)createFile("file.xml", "<a>x y</a>");
    XmlTag tag = file.getDocument().getRootTag();
    final XmlText xmlText = tag.getValue().getTextElements()[0];

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      try {
        xmlText.insertText("z", 1);
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }, "", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION));
    assertEquals("<a>xz y</a>", tag.getText());
  }

  private static String toDisplay(String text) {
    text = text.replaceAll("&lt;", "<");
    text = text.replaceAll("&gt;", ">");
    text = text.replaceAll("&amp;", "&");
    text = text.replaceAll("&apos;", "'");
    text = text.replaceAll("&quot;", "\"");
    text = text.replaceAll("<!\\[CDATA\\[", "");
    text = text.replaceAll("\\]\\]>", "");
    return text.replaceAll("&nbsp;", "_");
  }

  private static String toEscapedText(String original) {
    String text = original.replaceAll("<", "&lt;");
    text = text.replaceAll(">", "&gt;");
    text = text.replaceAll("&", "&amp;");
    text = text.replaceAll("'", "&apos;");
    text = text.replaceAll("\"", "&quot;");
    text = text.replaceAll("_", "&nbsp;");
    assertEquals(original, toDisplay(text));
    return text;
  }

  public void testCoordinateMappingConsistent() {
    doCoordinateMappingConsistentFromDisplayText("abc");
    doCoordinateMappingConsistentFromDisplayText(" abc");
    doCoordinateMappingConsistentFromDisplayText(" a bc");
    doCoordinateMappingConsistentFromDisplayText(" a b c");
    doCoordinateMappingConsistentFromDisplayText(" a b c ");
    doCoordinateMappingConsistentFromDisplayText(" ab c ");
    doCoordinateMappingConsistentFromDisplayText(" abc ");
    doCoordinateMappingConsistentFromDisplayText("abc ");

    doCoordinateMappingConsistentFromDisplayText(" ");
    doCoordinateMappingConsistentFromDisplayText("&");

    doCoordinateMappingConsistentFromDisplayText("&abc");
    doCoordinateMappingConsistentFromDisplayText(" &abc");
    doCoordinateMappingConsistentFromDisplayText(" a& bc");
    doCoordinateMappingConsistentFromDisplayText(" a &b c");
    doCoordinateMappingConsistentFromDisplayText(" a b& c ");
    doCoordinateMappingConsistentFromDisplayText(" ab c& ");
    doCoordinateMappingConsistentFromDisplayText(" ab&c ");
    doCoordinateMappingConsistentFromDisplayText("abc &");
    doCoordinateMappingConsistentFromDisplayText("abc&");
    doCoordinateMappingConsistentFromDisplayText("ab&c&");
    doCoordinateMappingConsistentFromDisplayText("ab&c");
    doCoordinateMappingConsistentFromDisplayText("a&b&c");

    doCoordinateMappingConsistentFromEscapedText("<![CDATA[ ]]>");
    doCoordinateMappingConsistentFromEscapedText("<![CDATA[x]]>");
    doCoordinateMappingConsistentFromEscapedText("xxx<![CDATA[x]]>");
    doCoordinateMappingConsistentFromEscapedText("xxx<![CDATA[x]]>xxx");
    doCoordinateMappingConsistentFromEscapedText("xxx&amp;<![CDATA[x]]>xxx");
    doCoordinateMappingConsistentFromEscapedText("xxx&amp;<![CDATA[x]]>&amp;xxx");
    doCoordinateMappingConsistentFromEscapedText("xxx<![CDATA[x]]>&amp;xxx");
    doCoordinateMappingConsistentFromEscapedText("xxx<![CDATA[xas]]>&amp;xxx");
    doCoordinateMappingConsistentFromEscapedText("xxx<![CDATA[xa>s]]>&amp;xxx");
    doCoordinateMappingConsistentFromEscapedText("xxx<![CDATA[x<a>s]]>&amp;xxx");
  }

  public void testNBSP() {
    final XmlTag tagA = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<a>1<b>&nbsp;</b></a>");

    final XmlTag tagB = tagA.findFirstSubTag("b");
    final XmlTagChild nbsp = tagB.getValue().getChildren()[0];
    assertEquals("&nbsp;", nbsp.getText());
    ApplicationManager.getApplication().runWriteAction(() -> {
      tagA.addBefore(nbsp.copy(), tagB);
    });

    XmlTagChild nbsp1 = tagA.getValue().getChildren()[0];
    assertEquals("1&nbsp;", nbsp1.getText());
  }

  private static void doCoordinateMappingConsistentFromDisplayText(final String text) throws IncorrectOperationException {
    doCoordinateMappingConsistentFromEscapedText(toEscapedText(text));
  }
                                               
  private static void doCoordinateMappingConsistentFromEscapedText(final String tagText) throws IncorrectOperationException {
    String text = toDisplay(tagText);
    XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>" + tagText + "</a>");
    final PsiElement[] children = tag.getValue().getTextElements();
    assertEquals(1, children.length);
    final XmlText xmlText = (XmlText)children[0];
    assertEquals(tagText, xmlText.getText());

    for (int i = 0; i <= text.length(); i++) {
      final int physical = xmlText.displayToPhysical(i);
      final int display = xmlText.physicalToDisplay(physical);
      assertEquals("Coords mapping failed for: '" + tagText + "' - " + physical, display, i);
    }

    assertEquals("Coords mapping failed for: '" + tagText + "'", 0, xmlText.physicalToDisplay(0));
    assertEquals(tagText.length(), xmlText.displayToPhysical(text.length()));
    assertEquals(text.length(), xmlText.physicalToDisplay(tagText.length()));
  }

  public void testStrangeCharactesInText() {
    ApplicationManager.getApplication().runWriteAction(() -> XmlElementFactory.getInstance(getProject()).createTagFromText("<a/>").getValue().setText("@#$%@$%$${${''}"));
  }

  public void testPsiToDocumentSynchronizationFailed() throws Throwable {
    String text = "<wpd><methods> </methods></wpd>";
    final File tempFile = FileUtil.createTempFile("idea-test", ".xml");
    tempFile.createNewFile();
    FileUtil.writeToFile(tempFile, text);

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      VirtualFileManager.getInstance().syncRefresh();
      XmlFile file ;//createTemporaryFile("wpd.xml", text));
      try {
        file = (XmlFile)getPsiManager().findFile(VfsUtil.findFileByURL(tempFile.toURL()));
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }

      final XmlTag methodTag = file.getDocument().getRootTag().findFirstSubTag("methods");
      try {
        methodTag.add(XmlElementFactory.getInstance(getProject()).createTagFromText("<method/>"));
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }, "", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION));
  }

  public void testXmlFormattingException() {
    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("<foo>bar</foo>");
    ApplicationManager.getApplication().runWriteAction(() -> {
      tag.add(XmlElementFactory.getInstance(getProject()).createTagFromText("<bar/>"));
    });
  }

  public void testSetNamespace() {
    XmlFile xhtmlFile = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("dummy.xml",
                                                                                                             "<html><body/></html>");

    final XmlTag rootTag = xhtmlFile.getDocument().getRootTag();

    rootTag.getSubTags()[0].getNamespace();  // fill the cache

    ApplicationManager.getApplication().runWriteAction(() -> {
      rootTag.setAttribute("xmlns", "http://www.ru");
    });

    assertEquals("http://www.ru", rootTag.getSubTags()[0].getNamespace());
  }

   public void testInsert() {
     String html = "<html><head /><body><hr /></body>\n</html>";
     XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("xxx.xhtml", html);
     XmlTag body = file.getDocument().getRootTag().findFirstSubTag("body");
     final XmlTag hr = body.getSubTags()[0];

     final XmlText text = XmlElementFactory.getInstance(getProject()).createDisplayText("p");
     ApplicationManager.getApplication().runWriteAction(() -> {
       PsiElement element = hr.getParentTag().addAfter(text, hr);
       assertEquals(element.getParent(), hr.getParentTag());
     });
   }

  public void testCollapse() throws IncorrectOperationException {
    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("<foo></foo>");
    ApplicationManager.getApplication().runWriteAction(() -> {
      tag.collapseIfEmpty();
      assertEquals("<foo/>", tag.getText());

      final XmlTag tag1 = XmlElementFactory.getInstance(getProject()).createTagFromText("<foo>abc</foo>");
      tag1.collapseIfEmpty();
      assertEquals("<foo/>", tag1.getText());

      final XmlTag tag2 = XmlElementFactory.getInstance(getProject()).createTagFromText("<foo><boo/></foo>");
      tag2.collapseIfEmpty();
      assertEquals("<foo><boo/></foo>", tag2.getText());
    });
  }

  public void testSetName() {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("dummy.xml", XmlFileType.INSTANCE, "<fooBarGoo>1</fooBarGoo>", 0, true);
    final XmlTag tag = file.getDocument().getRootTag();
    final Document document = file.getViewProvider().getDocument();
    ApplicationManager.getApplication().runWriteAction(() -> {
      tag.setName("xxx");
      assertEquals("<xxx>1</xxx>", tag.getText());
      assertEquals("<xxx>1</xxx>", document.getText());
    });
  }

  public void testSetAttributeValue() {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("dummy.xml", XmlFileType.INSTANCE, "<fooBarGoo attr>1</fooBarGoo>", 0, true);
    final XmlTag tag = file.getDocument().getRootTag();
    final Document document = file.getViewProvider().getDocument();
    ApplicationManager.getApplication().runWriteAction(() -> {
      tag.setAttribute("attr", "");
      assertEquals("<fooBarGoo attr=\"\">1</fooBarGoo>", tag.getText());
      assertEquals("<fooBarGoo attr=\"\">1</fooBarGoo>", document.getText());
    });
  }
}
