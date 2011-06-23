/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.impl.ElementLookupRenderer;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.PlatformIcons;

/**
 * @author yole
 */
public class PropertyLookupRenderer implements ElementLookupRenderer<Property> {
  public boolean handlesItem(final Object element) {
    return element instanceof Property;
  }

  public void renderElement(final LookupItem item, final Property property, final LookupElementPresentation presentation) {
    presentation.setIcon(PlatformIcons.PROPERTY_ICON);
    presentation.setItemText(property.getUnescapedKey());

    PropertiesFile propertiesFile = property.getContainingFile();
    ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    String value = property.getValue();
    boolean hasBundle = resourceBundle != ResourceBundleImpl.NULL;
    if (hasBundle) {
      PropertiesFile defaultPropertiesFile = resourceBundle.getDefaultPropertiesFile(propertiesFile.getProject());
      Property defaultProperty = defaultPropertiesFile.findPropertyByKey(property.getUnescapedKey());
      if (defaultProperty != null) {
        value = defaultProperty.getValue();
      }
    }

    if (presentation.isReal() && value != null && value.length() > 10) value = value.substring(0, 10) + "...";

    TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_VALUE);
    presentation.setTailText("="+ value, attrs.getForegroundColor());
    if (hasBundle) {
      presentation.setTypeText(resourceBundle.getBaseName(), PropertiesFileType.FILE_ICON);
    }
  }
}
