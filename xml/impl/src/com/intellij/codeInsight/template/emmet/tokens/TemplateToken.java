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
package com.intellij.codeInsight.template.emmet.tokens;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.XmlEmmetParser;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class TemplateToken extends ZenCodingToken {
  public static final String ATTRS = "ATTRS";
  public final static TemplateToken EMPTY_TEMPLATE_TOKEN = new TemplateToken("", Collections.<String, String>emptyMap());

  @NotNull private final String myKey;
  private TemplateImpl myTemplate;
  @NotNull private final Map<String, String> myAttributes;
  private XmlFile myFile;

  public TemplateToken(@NotNull String key) {
    this(key, Collections.<String, String>emptyMap());
  }

  public TemplateToken(@NotNull String key, @NotNull Map<String, String> attribute2value) {
    myKey = key;
    myAttributes = attribute2value;
  }

  @NotNull
  public Map<String, String> getAttributes() {
    return myAttributes;
  }

  public XmlFile getFile() {
    return myFile;
  }

  public void setFile(XmlFile file) {
    myFile = file;
  }

  @NotNull
  public String getKey() {
    return myKey;
  }

  public boolean setTemplate(@NotNull TemplateImpl template, @NotNull CustomTemplateCallback callback) {
    myTemplate = template;
    final XmlFile xmlFile = parseXmlFileInTemplate(template, callback, getAttributes());
    setFile(xmlFile);
    final XmlTag tag = xmlFile.getRootTag();
    if (!getAttributes().isEmpty() && tag == null) {
      return false;
    }
    return true;
  }


  private static boolean containsAttrsVar(@NotNull TemplateImpl template) {
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (ATTRS.equals(varName)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static XmlFile parseXmlFileInTemplate(@NotNull TemplateImpl template, @NotNull CustomTemplateCallback callback,
                                                @NotNull Map<String, String> attributes) {
    XmlTag dummyRootTag = null;
    String templateString = template.getString();
    final PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(callback.getProject());
    if (!containsAttrsVar(template)) {
      XmlFile dummyFile = (XmlFile)psiFileFactory.createFileFromText("dummy.html", HTMLLanguage.INSTANCE, templateString, false, true);
      dummyRootTag = dummyFile.getRootTag();
      if (dummyRootTag != null) {
        addMissingAttributes(dummyRootTag, attributes);
      }
    }

    templateString = dummyRootTag != null ? dummyRootTag.getContainingFile().getText() : templateString;
    XmlFile xmlFile =
      (XmlFile)psiFileFactory.createFileFromText("dummy.xml", StdFileTypes.XML, templateString, LocalTimeCounter.currentTime(), true);
    VirtualFile vFile = xmlFile.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    return xmlFile;
  }


  private static void addMissingAttributes(@NotNull XmlTag tag, @NotNull Map<String, String> attributes) {
    for (Map.Entry<String, String> attribute : attributes.entrySet()) {
      if (!XmlEmmetParser.DEFAULT_ATTRIBUTE_NAME.equals(attribute.getKey()) && tag.getAttribute(attribute.getKey()) == null) {
        XmlTag htmlTag = XmlElementFactory.getInstance(tag.getProject()).createHTMLTagFromText("<dummy " + attribute.getKey() + "=\"\"/>");
        final XmlAttribute newAttribute = ArrayUtil.getFirstElement(htmlTag.getAttributes());
        if (newAttribute != null) {
          tag.add(newAttribute);
        }
      }
    }
  }

  @Nullable
  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  @NotNull
  public String toString() {
    return "TEMPLATE";
  }
}
