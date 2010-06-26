/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateInvokationListener;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashSet;
import com.intellij.xml.util.HtmlUtil;
import org.apache.xerces.util.XML11Char;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlZenCodingTemplate extends ZenCodingTemplate {
  private static final String SELECTORS = ".#[";
  private static final String ID = "id";
  private static final String CLASS = "class";
  private static final String DEFAULT_TAG = "div";

  private static String getPrefix(@NotNull String templateKey) {
    for (int i = 0, n = templateKey.length(); i < n; i++) {
      char c = templateKey.charAt(i);
      if (SELECTORS.indexOf(c) >= 0) {
        return templateKey.substring(0, i);
      }
    }
    return templateKey;
  }

  @Nullable
  private static Pair<String, String> parseAttrNameAndValue(@NotNull String text) {
    int eqIndex = text.indexOf('=');
    if (eqIndex > 0) {
      return new Pair<String, String>(text.substring(0, eqIndex), text.substring(eqIndex + 1));
    }
    return null;
  }

  @Nullable
  private static XmlTemplateToken parseSelectors(@NotNull String text) {
    String templateKey = null;
    List<Pair<String, String>> attributes = new ArrayList<Pair<String, String>>();
    Set<String> definedAttrs = new HashSet<String>();
    final List<String> classes = new ArrayList<String>();
    StringBuilder builder = new StringBuilder();
    char lastDelim = 0;
    text += MARKER;
    int classAttrPosition = -1;
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (c == '#' || c == '.' || c == '[' || c == ']' || i == n - 1) {
        if (c != ']') {
          switch (lastDelim) {
            case 0:
              templateKey = builder.toString();
              break;
            case '#':
              if (!definedAttrs.add(ID)) {
                return null;
              }
              attributes.add(new Pair<String, String>(ID, builder.toString()));
              break;
            case '.':
              if (builder.length() <= 0) {
                return null;
              }
              if (classAttrPosition < 0) {
                classAttrPosition = attributes.size();
              }
              classes.add(builder.toString());
              break;
            case ']':
              if (builder.length() > 0) {
                return null;
              }
              break;
            default:
              return null;
          }
        }
        else if (lastDelim != '[') {
          return null;
        }
        else {
          Pair<String, String> pair = parseAttrNameAndValue(builder.toString());
          if (pair == null || !definedAttrs.add(pair.first)) {
            return null;
          }
          attributes.add(pair);
        }
        lastDelim = c;
        builder = new StringBuilder();
      }
      else {
        builder.append(c);
      }
    }
    if (classes.size() > 0) {
      if (definedAttrs.contains(CLASS)) {
        return null;
      }
      StringBuilder classesAttrValue = new StringBuilder();
      for (int i = 0; i < classes.size(); i++) {
        classesAttrValue.append(classes.get(i));
        if (i < classes.size() - 1) {
          classesAttrValue.append(' ');
        }
      }
      assert classAttrPosition >= 0;
      attributes.add(classAttrPosition, new Pair<String, String>(CLASS, classesAttrValue.toString()));
    }
    return new XmlTemplateToken(templateKey, attributes);
  }

  private static boolean isXML11ValidQName(String str) {
    final int colon = str.indexOf(':');
    if (colon == 0 || colon == str.length() - 1) {
      return false;
    }
    if (colon > 0) {
      final String prefix = str.substring(0, colon);
      final String localPart = str.substring(colon + 1);
      return XML11Char.isXML11ValidNCName(prefix) && XML11Char.isXML11ValidNCName(localPart);
    }
    return XML11Char.isXML11ValidNCName(str);
  }

  public static boolean isTrueXml(CustomTemplateCallback callback) {
    return isTrueXml(callback.getFileType());
  }

  public static boolean isTrueXml(FileType type) {
    return type == StdFileTypes.XHTML || type == StdFileTypes.JSPX || type == StdFileTypes.XML;
  }

  private static boolean isHtml(CustomTemplateCallback callback) {
    FileType type = callback.getFileType();
    return type == StdFileTypes.HTML || type == StdFileTypes.XHTML;
  }

  @Override
  @Nullable
  protected TemplateToken parseTemplateKey(String key, CustomTemplateCallback callback) {
    String prefix = getPrefix(key);
    boolean useDefaultTag = false;
    if (prefix.length() == 0) {
      if (!isHtml(callback)) {
        return null;
      }
      else {
        useDefaultTag = true;
        prefix = DEFAULT_TAG;
        key = prefix + key;
      }
    }
    TemplateImpl template = callback.findApplicableTemplate(prefix);
    if (template == null && !isXML11ValidQName(prefix)) {
      return null;
    }
    final XmlTemplateToken token = parseSelectors(key);
    if (token == null) {
      return null;
    }
    if (useDefaultTag && token.getAttribute2Value().size() == 0) {
      return null;
    }
    if (template == null) {
      template = generateTagTemplate(token.getKey(), callback);
    }
    assert prefix.equals(token.getKey());
    token.setTemplate(template);
    final XmlTag tag = parseXmlTagInTemplate(template.getString(), callback, true);
    if (token.getAttribute2Value().size() > 0 && tag == null) {
      return null;
    }
    if (tag != null) {
      if (!XmlZenCodingInterpreter.containsAttrsVar(template) && token.getAttribute2Value().size() > 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            addMissingAttributes(tag, token.getAttribute2Value());
          }
        });
      }
      token.setTag(tag);
    }
    return token;
  }

  private static void addMissingAttributes(XmlTag tag, List<Pair<String, String>> value) {
    List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(value);
    for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
      Pair<String, String> pair = iterator.next();
      if (tag.getAttribute(pair.first) != null) {
        iterator.remove();
      }
    }
    addAttributesBefore(tag, attr2value);
  }

  private static void addAttributesBefore(XmlTag tag, List<Pair<String, String>> attr2value) {
    XmlAttribute[] attributes = tag.getAttributes();
    XmlAttribute firstAttribute = attributes.length > 0 ? attributes[0] : null;
    XmlElementFactory factory = XmlElementFactory.getInstance(tag.getProject());
    for (Pair<String, String> pair : attr2value) {
      XmlAttribute xmlAttribute = factory.createXmlAttribute(pair.first, "");
      if (firstAttribute != null) {
        tag.addBefore(xmlAttribute, firstAttribute);
      }
      else {
        tag.add(xmlAttribute);
      }
    }
  }

  @NotNull
  private static TemplateImpl generateTagTemplate(String tagName, CustomTemplateCallback callback) {
    StringBuilder builder = new StringBuilder("<");
    builder.append(tagName).append('>');
    if (isTrueXml(callback) || !HtmlUtil.isSingleHtmlTag(tagName)) {
      builder.append("$END$</").append(tagName).append('>');
    }
    return new TemplateImpl("", builder.toString(), "");
  }

  @Nullable
  static XmlTag parseXmlTagInTemplate(String templateString, CustomTemplateCallback callback, boolean createPhysicalFile) {
    XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(callback.getProject())
      .createFileFromText("dummy.xml", StdFileTypes.XML, templateString, LocalTimeCounter.currentTime(), createPhysicalFile);
    VirtualFile vFile = xmlFile.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoManager.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    XmlDocument document = xmlFile.getDocument();
    return document == null ? null : document.getRootTag();
  }

  protected boolean isApplicable(@NotNull PsiElement element) {
    if (element.getLanguage() instanceof XMLLanguage) {
      if (PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class) != null) {
        return false;
      }
      if (PsiTreeUtil.getParentOfType(element, XmlComment.class) != null) {
        return false;
      }
      if (!findApplicableFilter(element)) {
        return false;
      }
      return true;
    }
    return false;
  }

  private static boolean findApplicableFilter(@NotNull PsiElement context) {
    for (ZenCodingFilter filter : ZenCodingFilter.EP_NAME.getExtensions()) {
      if (filter.isMyContext(context)) {
        return true;
      }
    }
    return new XmlZenCodingFilterImpl().isMyContext(context);
  }

  public static boolean startZenCoding(Editor editor, PsiFile file, String abbreviation) {
    int caretAt = editor.getCaretModel().getOffset();
    XmlZenCodingTemplate template = CustomLiveTemplate.EP_NAME.findExtension(XmlZenCodingTemplate.class);
    if (abbreviation != null && !template.supportsWrapping()) {
      return false;
    }
    if (template.isApplicable(file, caretAt)) {
      final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
      if (abbreviation != null) {
        String selection = callback.getEditor().getSelectionModel().getSelectedText();
        assert selection != null;
        selection = selection.trim();
        template.doWrap(selection, abbreviation, callback, new TemplateInvokationListener() {
          public void finished() {
            callback.startAllExpandedTemplates();
          }
        });
      }
      else {
        String key = template.computeTemplateKey(callback);
        if (key != null) {
          template.expand(key, callback);
          callback.startAllExpandedTemplates();
          return true;
        }
        // if it is simple live template invokation, we should start it using TemplateManager because template may be ambiguous
        /*TemplateManager manager = TemplateManager.getInstance(file.getProject());
        return manager.startTemplate(editor, TemplateSettings.getInstance().getDefaultShortcutChar());*/
      }
    }
    return false;
  }

  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    PsiElement element = callback.getContext();
    int line = editor.getCaretModel().getLogicalPosition().line;
    int lineStart = editor.getDocument().getLineStartOffset(line);
    int elementStart;
    do {
      elementStart = element.getTextRange().getStartOffset();
      int startOffset = elementStart > lineStart ? elementStart : lineStart;
      String key = computeKey(editor, startOffset);
      if (checkTemplateKey(key, callback)) {
        return key;
      }
      element = element.getParent();
    }
    while (element != null && elementStart > lineStart);
    return null;
  }

  public boolean supportsWrapping() {
    return true;
  }
}