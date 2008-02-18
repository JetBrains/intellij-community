package com.intellij.lang.properties;

import com.intellij.codeInsight.lookup.impl.ElementLookupRenderer;
import com.intellij.codeInsight.lookup.impl.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.colors.EditorColorsManager;

/**
 * @author yole
 */
public class PropertyLookupRenderer implements ElementLookupRenderer<Property> {
  public boolean handlesItem(final Object element) {
    return element instanceof Property;
  }

  public void renderElement(final LookupItem item, final Property property, final LookupElementPresentation presentation) {
    presentation.setItemText(property.getUnescapedKey());

    PropertiesFile propertiesFile = property.getContainingFile();
    PropertiesFile defaultPropertiesFile = propertiesFile.getResourceBundle().getDefaultPropertiesFile(propertiesFile.getProject());
    Property defaultProperty = defaultPropertiesFile.findPropertyByKey(property.getUnescapedKey());
    String value = defaultProperty == null ? property.getValue() : defaultProperty.getValue();
    if (presentation.trimText() && value != null && value.length() > 10) value = value.substring(0, 10) + "...";

    TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_VALUE);
    presentation.setTailText("="+ value, attrs.getForegroundColor(), true);

    presentation.setTypeText(property.getContainingFile().getResourceBundle().getBaseName(), PropertiesFileType.FILE_ICON);
  }
}
