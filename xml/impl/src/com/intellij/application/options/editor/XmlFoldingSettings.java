/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.xml.XmlBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class XmlFoldingSettings implements NamedJDOMExternalizable, ExportableApplicationComponent {

  public static XmlFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(XmlFoldingSettings.class);
  }

  public boolean isCollapseXmlTags() {
    return COLLAPSE_XML_TAGS;
  }

  public void setCollapseXmlTags(boolean value) {
    COLLAPSE_XML_TAGS = value;
  }

  @SuppressWarnings({"WeakerAccess"}) public boolean COLLAPSE_XML_TAGS = false;

  @NotNull
  public String getComponentName() {
    return "XmlFoldingSettings";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public String getExternalFileName() {
    return CodeInsightSettings.EXTERNAL_FILE_NAME;
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return XmlBundle.message("xml.folding.settings");
  }
}