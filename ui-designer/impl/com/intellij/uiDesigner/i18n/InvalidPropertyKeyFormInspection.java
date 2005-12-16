package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.inspections.FormErrorCollector;
import com.intellij.uiDesigner.inspections.StringDescriptorInspection;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.jetbrains.annotations.Nullable;

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
      collector.addError(prop, error, null);
    }
  }

  @Nullable
  private String checkDescriptor(final StringDescriptor descriptor, final Module module) {
    final String bundleName = descriptor.getBundleName();
    final String key = descriptor.getKey();
    if (bundleName == null && key == null) return null;
    if (bundleName == null) {
      return  CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.bundle.not.specified");
    }

    if (key == null) {
      return CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.property.key.not.specified");
    }


    PropertiesFile bundle = PropertiesUtil.getPropertiesFile(bundleName, module);
    if (bundle == null) {
      return CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.bundle.not.found", bundle);
    }


    final Property property = bundle.findPropertyByKey(key);
    if (property == null) {
      return CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.key.not.found", key, bundleName);
    }
    return null;
  }
}
