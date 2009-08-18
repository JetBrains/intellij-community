package com.intellij.lang.properties;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.ElementLookupRenderer;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.Icons;

/**
 * @author yole
 */
public class PropertyLookupRenderer implements ElementLookupRenderer<Property> {
  public boolean handlesItem(final Object element) {
    return element instanceof Property;
  }

  public void renderElement(final LookupItem item, final Property property, final LookupElementPresentation presentation) {
    presentation.setIcon(Icons.PROPERTY_ICON);
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
