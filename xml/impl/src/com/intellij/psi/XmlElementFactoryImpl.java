package com.intellij.psi;

import com.intellij.lang.ASTFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlTagTextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementFactoryImpl extends XmlElementFactory {

  private final Project myProject;

  public XmlElementFactoryImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public XmlTag createTagFromText(@NotNull String text) throws IncorrectOperationException {
    final XmlDocument document = createXmlDocument(text, "dummy.xml");
    final XmlTag tag = document.getRootTag();
    if (tag == null) throw new IncorrectOperationException("Incorrect tag text");
    return tag;
  }

  @NotNull
  public XmlAttribute createXmlAttribute(@NotNull String name, String value) throws IncorrectOperationException {
    final XmlDocument document = createXmlDocument("<tag " + name + "=\"" + value + "\"/>", "dummy.xml");
    XmlTag tag = document.getRootTag();
    assert tag != null;
    return tag.getAttributes()[0];
  }

  @NotNull
  public XmlText createDisplayText(@NotNull String s) throws IncorrectOperationException {
    final XmlTag tagFromText = createTagFromText("<a>" + XmlTagTextUtil.getCDATAQuote(s) + "</a>");
    final XmlText[] textElements = tagFromText.getValue().getTextElements();
    if (textElements.length == 0) return (XmlText)ASTFactory.composite(XmlElementType.XML_TEXT);
    return textElements[0];
  }

  @NotNull
  public XmlTag createXHTMLTagFromText(@NotNull String text) throws IncorrectOperationException {
    final XmlDocument document = createXmlDocument(text, "dummy.xhtml");
    final XmlTag tag = document.getRootTag();
    assert tag != null;
    return tag;
  }

  private XmlDocument createXmlDocument(@NonNls final String text, @NonNls final String fileName) {
    final XmlDocument document = ((XmlFile)PsiFileFactory.getInstance(myProject).createFileFromText(fileName, text)).getDocument();
    assert document != null;
    return document;
  }
}
