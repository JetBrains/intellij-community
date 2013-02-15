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
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class TemplateToken extends ZenCodingToken {
  public static final String ATTRS = "ATTRS";
  public final static TemplateToken EMPTY_TEMPLATE_TOKEN = new TemplateToken("", new ArrayList<Pair<String, String>>());

  private final String myKey;
  private TemplateImpl myTemplate;
  private final List<Pair<String, String>> myAttribute2Value;
  private XmlFile myFile;

  public TemplateToken(String key) {
    this(key, new ArrayList<Pair<String, String>>());
  }

  public TemplateToken(String key, List<Pair<String, String>> attribute2value) {
    myKey = key;
    myAttribute2Value = attribute2value;
  }

  public List<Pair<String, String>> getAttribute2Value() {
    return myAttribute2Value;
  }

  public XmlFile getFile() {
    return myFile;
  }

  public void setFile(XmlFile file) {
    myFile = file;
  }

  public String getKey() {
    return myKey;
  }

  public boolean setTemplate(TemplateImpl template, CustomTemplateCallback callback) {
    myTemplate = template;
    final XmlFile xmlFile = parseXmlFileInTemplate(template.getString(), callback, true);
    setFile(xmlFile);
    XmlDocument document = xmlFile.getDocument();
    final XmlTag tag = document != null ? document.getRootTag() : null;
    if (getAttribute2Value().size() > 0 && tag == null) {
      return false;
    }
    if (tag != null) {
      if (!containsAttrsVar(template) && getAttribute2Value().size() > 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            addMissingAttributes(tag, getAttribute2Value());
          }
        });
      }
    }
    return true;
  }


  private static boolean containsAttrsVar(TemplateImpl template) {
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (ATTRS.equals(varName)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static XmlFile parseXmlFileInTemplate(String templateString, CustomTemplateCallback callback, boolean createPhysicalFile) {
    XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(callback.getProject())
      .createFileFromText("dummy.xml", StdFileTypes.XML, templateString, LocalTimeCounter.currentTime(), createPhysicalFile);
    VirtualFile vFile = xmlFile.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    return xmlFile;
  }


  private static void addMissingAttributes(XmlTag tag, List<Pair<String, String>> value) {
    List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(value);
    for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext(); ) {
      Pair<String, String> pair = iterator.next();
      if (tag.getAttribute(pair.first) != null) {
        iterator.remove();
      }
    }
    addAttributesBefore(tag, attr2value);
  }

  private static void addAttributesBefore(XmlTag tag, List<Pair<String, String>> attr2value) {
    XmlAttribute firstAttribute = ArrayUtil.getFirstElement(tag.getAttributes());
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

  @Nullable
  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  public String toString() {
    return "TEMPLATE";
  }
}
