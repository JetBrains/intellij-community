// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;

public class XmlCodeEditUtilTest extends LightJavaCodeInsightTestCase {
  public void testXHTML() {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.xhtml", XHtmlFileType.INSTANCE,
                          "<a>\n" + "    <b/>\n" + "\n" + "\n" + "\n" + "\n" + "\n" + "\n" + "\n" + "\n" + "\n" + "\n" + "</a>");

    XmlTag rootTag = file.getDocument().getRootTag();
    XmlTag finalRootTag = rootTag;
    WriteCommandAction.runWriteCommandAction(null, () -> finalRootTag.getSubTags()[0].delete());

    assertEquals("<a>\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "\n" +
                 "</a>",
                 file.getText());

    file = (XmlFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.xhtml", XHtmlFileType.INSTANCE, "<html><body><div>text</div></body></html>");
    rootTag = file.getDocument().getRootTag();
    XmlTag finalRootTag1 = rootTag;
    WriteCommandAction.runWriteCommandAction(null, () -> {
      finalRootTag1.getSubTags()[0].getSubTags()[0].setAttribute("a", "");
    });

    assertEquals("<html><body><div a=\"\">text</div></body></html>", file.getText());
  }

  public void testAddLayoutSubTag() {
    XmlTag tag = XmlElementFactory.getInstance(getProject())
      .createTagFromText("<fabrique-element element=\"wpd\" name=\"Index\">\n" +
                         "    <wpd/>\n" +
                         "</fabrique-element>");

    tag.add(tag.createChildTag("layout", "",
                               "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                               "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0  Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
                               "<fabrique:inheritorLayout/>", false));
  }

  public void testAddTag() {
    Project ideaProject = getProject();
    PsiManager psiManager = PsiManager.getInstance(ideaProject);
    XmlFile fileFromText = (XmlFile)PsiFileFactory.getInstance(psiManager.getProject())
      .createFileFromText("sample.xhtml", XHtmlFileType.INSTANCE, "<html><body><p/></body></html>", LocalTimeCounter.currentTime(), true);
    XmlTag htmlTag = fileFromText.getDocument().getRootTag();
    XmlTag bodyTag = htmlTag.getSubTags()[0];
    XmlTag tagP = bodyTag.getSubTags()[0];
    //do not remove: to hold the reference
    //noinspection unused
    Document document = PsiDocumentManager.getInstance(ideaProject).getDocument(htmlTag.getContainingFile());

    execute(() -> CodeStyleManager.getInstance(ideaProject).performActionWithFormatterDisabled((Runnable)() -> {
      try {
        XmlTag childTag = XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText("<p/>");
        bodyTag.addAfter(childTag, tagP);
      }
      catch (IncorrectOperationException ignored) {
      }
    }));
    assertEquals("<html><body><p/><p/></body></html>", htmlTag.getText());
  }

  private void execute(Runnable runnable) {
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance()
      .executeCommand(getProject(), runnable, "", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION));
  }

  public void testKuralenok_DeleteTagKeepingContent() {
    XmlTag aTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>a <b>123 <z/> ff</b> c</a>");
    XmlTag bTag = aTag.findFirstSubTag("b");

    CodeStyle.getSettings(getProject()).getCustomSettings(XmlCodeStyleSettings.class).XML_KEEP_WHITESPACES = true;

    XmlTagChild[] valueChildren = bTag.getValue().getChildren();
    aTag.addRangeBefore(valueChildren[0], valueChildren[valueChildren.length - 1], bTag);

    WriteCommandAction.runWriteCommandAction(null, () -> bTag.delete());

    assertEquals("<a>a 123 <z/> ff c</a>", aTag.getText());
  }

  public void testSCR1542() {
    String html = "<html><head /><body><hr /></body>\n</html>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.html", HtmlFileType.INSTANCE, html);
    XmlTag body = file.getDocument().getRootTag().findFirstSubTag("body");
    XmlTag hr = body.getSubTags()[0];

    XmlText text = XmlElementFactory.getInstance(getProject()).createDisplayText("p");
    PsiElement element = body.addAfter(text, hr);
    assertEquals(body, element.getParent());
  }

  public void testDeleteTable() {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.xhtml",
                                                                                        XHtmlFileType.INSTANCE,
                                                                                        "<html>\n" +
                                                                                        "<body xmlns:fabrique=\"https://www.jetbrains.com/schemas/fabrique/page-template\"><p>\n" +
                                                                                        " ujhgjhgjhg<table style=\"width: 82px; height: 84px\"><tr><td>&nbsp;</td><td>&nbsp;</td></tr><tr><td>&nbsp;</td><td>&nbsp;</td></tr></table>\n" +
                                                                                        " jhgjhgjhgjhgjhg</p>\n" +
                                                                                        "</body>\n" +
                                                                                        "</html>");
    XmlTag html = file.getDocument().getRootTag();
    XmlTag table = html.findFirstSubTag("body").findFirstSubTag("p").findFirstSubTag("table");
    assertEquals("table", table.getName());

    WriteCommandAction.runWriteCommandAction(null, () -> table.delete());

    assertEquals("<html>\n" +
                 "<body xmlns:fabrique=\"https://www.jetbrains.com/schemas/fabrique/page-template\"><p>\n" +
                 " ujhgjhgjhg\n" +
                 " jhgjhgjhgjhgjhg</p>\n" +
                 "</body>\n" +
                 "</html>", file.getText());
  }

  public void testReplaceTag() {
    String html = "<html>\n" +
                  "<head />\n" +
                  "<body xmlns:fabrique=\"https://www.jetbrains.com/schemas/fabrique/page-template\">\n"

                  +
                  "    <div style=\"border: 1px solid green; margin: 2px; padding: 2px;\"><p><span style=\"color: red\"><span\n" +
                  "            style=\"font-size: 15pt\">12345678</span></span></p></div>\n" +
                  "\n" +
                  "</body>\n" +
                  "</html>";

    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.xhtml", XHtmlFileType.INSTANCE, html);
    XmlTag body = file.getDocument().getRootTag().findFirstSubTag("body");
    XmlTag span = body.findFirstSubTag("div").findFirstSubTag("p").findFirstSubTag("span").findFirstSubTag("span");

    String text = "<span\n        style=\"font-size:15pt\">1234</span>";
    XmlTag[] tag = {XmlElementFactory.getInstance(getProject()).createXHTMLTagFromText(text)};

    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag[0] = (XmlTag)span.replace(tag[0]);
    });
    assertEquals(text, tag[0].getText());
  }
}
