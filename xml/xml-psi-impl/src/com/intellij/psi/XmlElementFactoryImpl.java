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
package com.intellij.psi;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementFactoryImpl extends XmlElementFactory {

  private final Project myProject;

  public XmlElementFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public XmlTag createTagFromText(@NotNull @NonNls CharSequence text, @NotNull Language language) throws IncorrectOperationException {
    final FileType type = getFileType(language);
    final XmlDocument document = createXmlDocument(text, "dummy." + type.getDefaultExtension(), type);
    final XmlTag tag = document.getRootTag();
    if (tag == null) throw new IncorrectOperationException("Incorrect tag text");
    return tag;
  }

  @Override
  @NotNull
  public XmlTag createTagFromText(@NotNull CharSequence text) throws IncorrectOperationException {
    return createTagFromText(text, XMLLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public XmlAttribute createXmlAttribute(@NotNull String name, @NotNull String value) throws IncorrectOperationException {
    return createAttribute(name, quoteValue(value), XmlFileType.INSTANCE);
  }

  @NotNull
  @Override
  public XmlAttribute createAttribute(@NotNull @NonNls String name, @NotNull String value, @Nullable PsiElement context)
    throws IncorrectOperationException {
    return createAttribute(name, quoteValue(value), getFileType(context));
  }

  @Override
  public @NotNull XmlAttribute createAttribute(@NotNull String name,
                                               @NotNull String value,
                                               @Nullable Character quoteStyle,
                                               @Nullable PsiElement context) throws IncorrectOperationException {
    return createAttribute(name, quoteValue(value, quoteStyle), getFileType(context));
  }

  @NotNull
  private XmlAttribute createAttribute(@NotNull String name, @NotNull String quotedValue, @NotNull FileType fileType) {
    final XmlDocument document = createXmlDocument("<tag " + name + "=" + quotedValue + "/>",
                                                   "dummy." + fileType.getDefaultExtension(), fileType);
    XmlTag tag = document.getRootTag();
    assert tag != null;
    XmlAttribute[] attributes = tag.getAttributes();
    LOG.assertTrue(attributes.length == 1, document.getText());
    return attributes[0];
  }

  @NotNull
  public static String quoteValue(@NotNull String value, @Nullable Character quoteStyle) {
    if (quoteStyle != null) {
      if (quoteStyle == '\'') {
        return quoteStyle + StringUtil.replace(value, "'", "&apos;") + quoteStyle;
      }
      else if (quoteStyle == '"') {
        return quoteStyle + StringUtil.replace(value, "\"", "&quot;") + quoteStyle;
      }
    }
    return quoteValue(value);
  }

  @NotNull
  public static String quoteValue(@NotNull String value) {
    final char quoteChar;
    if (!value.contains("\"")) {
      quoteChar = '"';
    }
    else if (!value.contains("'")) {
      quoteChar = '\'';
    }
    else {
      quoteChar = '"';
      value = StringUtil.replace(value, "\"", "&quot;");
    }
    return quoteChar + value + quoteChar;
  }

  @Override
  @NotNull
  public XmlText createDisplayText(@NotNull String s) throws IncorrectOperationException {
    final XmlTag tagFromText = createTagFromText("<a>" + XmlTagUtil.getCDATAQuote(s) + "</a>");
    final XmlText[] textElements = tagFromText.getValue().getTextElements();
    if (textElements.length == 0) return (XmlText)ASTFactory.composite(XmlElementType.XML_TEXT);
    return textElements[0];
  }

  @Override
  @NotNull
  public XmlTag createXHTMLTagFromText(@NotNull String text) throws IncorrectOperationException {
    final XmlDocument document = createXmlDocument(text, "dummy.xhtml", XHtmlFileType.INSTANCE);
    final XmlTag tag = document.getRootTag();
    assert tag != null;
    return tag;
  }

  @Override
  @NotNull
  public XmlTag createHTMLTagFromText(@NotNull String text) throws IncorrectOperationException {
    final XmlDocument document = createXmlDocument(text, "dummy.html", HtmlFileType.INSTANCE);
    final XmlTag tag = document.getRootTag();
    assert tag != null;
    return tag;
  }

  private XmlDocument createXmlDocument(@NonNls final CharSequence text, @NonNls final String fileName, FileType fileType) {
    PsiFile fileFromText = PsiFileFactory.getInstance(myProject).createFileFromText(fileName, fileType, text);

    XmlFile xmlFile;
    if (fileFromText instanceof XmlFile) {
      xmlFile = (XmlFile)fileFromText;
    }
    else {
      xmlFile = (XmlFile)fileFromText.getViewProvider().getPsi(((LanguageFileType)fileType).getLanguage());
      assert xmlFile != null;
    }
    XmlDocument document = xmlFile.getDocument();
    assert document != null;
    return document;
  }

  @NotNull
  private static FileType getFileType(@Nullable PsiElement context) {
    if (context == null) {
      return XmlFileType.INSTANCE;
    }
    if (context.getLanguage().isKindOf(HTMLLanguage.INSTANCE)) {
      return getFileType(context.getLanguage());
    }
    return PsiTreeUtil.getParentOfType(context, XmlTag.class, false) instanceof HtmlTag
           ? HtmlFileType.INSTANCE : XmlFileType.INSTANCE;
  }

  private static FileType getFileType(@NotNull Language language) {
    assert language instanceof XMLLanguage : "Tag can be created only for xml language";
    FileType type = language.getAssociatedFileType();
    return type == null ? XmlFileType.INSTANCE : type;
  }

  private static final Logger LOG = Logger.getInstance(XmlElementFactoryImpl.class);
}
