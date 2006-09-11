package com.intellij.uiDesigner.i18n;

import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.inspections.FormErrorCollector;
import com.intellij.uiDesigner.inspections.StringDescriptorInspection;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class InvalidPropertyKeyFormInspection extends StringDescriptorInspection {
  public InvalidPropertyKeyFormInspection() {
    super("UnresolvedPropertyKey");
  }

  protected void checkStringDescriptor(final StringDescriptorType property,
                                       final Module module,
                                       final IComponent component,
                                       final IProperty prop,
                                       final StringDescriptor descriptor,
                                       final FormErrorCollector collector) {
    String error = checkDescriptor(descriptor, module);
    if (error != null) {
      collector.addError(getID(), prop, error, null);
    }
  }

  @Nullable
  private static String checkDescriptor(final StringDescriptor descriptor, final Module module) {
    final String bundleName = descriptor.getDottedBundleName();
    final String key = descriptor.getKey();
    if (bundleName == null && key == null) return null;
    if (bundleName == null) {
      return UIDesignerBundle.message("inspection.invalid.property.in.form.quickfix.error.bundle.not.specified");
    }

    if (key == null) {
      return UIDesignerBundle.message("inspection.invalid.property.in.form.quickfix.error.property.key.not.specified");
    }

    PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(module.getProject());
    List<PropertiesFile> propFiles = manager.findPropertiesFiles(module, bundleName);

    if (propFiles.size() == 0) {
      return UIDesignerBundle.message("inspection.invalid.property.in.form.quickfix.error.bundle.not.found", bundleName);
    }

    for(PropertiesFile propFile: propFiles) {
      final Property property = propFile.findPropertyByKey(key);
      if (property == null) {
        return UIDesignerBundle.message("inspection.invalid.property.in.form.quickfix.error.key.not.found",
                                         key, bundleName, propFile.getLocale().getDisplayName());
      }
    }
    return null;
  }
}
