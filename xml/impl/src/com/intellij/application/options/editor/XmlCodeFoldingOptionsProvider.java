/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.xml.XmlBundle;

public class XmlCodeFoldingOptionsProvider extends BeanConfigurable<XmlFoldingSettings.State> implements CodeFoldingOptionsProvider {
  public XmlCodeFoldingOptionsProvider() {
    super(XmlFoldingSettings.getInstance().getState(), XmlBundle.message("options.xml.display.name"));
    XmlFoldingSettings settings = XmlFoldingSettings.getInstance();
    checkBox(XmlBundle.message("checkbox.collapse.xml.tags"), settings::isCollapseXmlTags, value->settings.getState().COLLAPSE_XML_TAGS=value);
    checkBox(XmlBundle.message("checkbox.collapse.html.style.attribute"),settings::isCollapseHtmlStyleAttribute, value->settings.getState().COLLAPSE_HTML_STYLE_ATTRIBUTE=value);
    checkBox(XmlBundle.message("checkbox.collapse.entities"),settings::isCollapseEntities, value->settings.getState().COLLAPSE_ENTITIES=value);
    checkBox(XmlBundle.message("checkbox.collapse.data.uri"),settings::isCollapseDataUri, value->settings.getState().COLLAPSE_DATA_URI=value);
  }
}