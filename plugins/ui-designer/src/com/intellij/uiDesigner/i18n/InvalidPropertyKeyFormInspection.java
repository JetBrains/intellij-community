// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.i18n;

import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.inspections.FormErrorCollector;
import com.intellij.uiDesigner.inspections.StringDescriptorInspection;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class InvalidPropertyKeyFormInspection extends StringDescriptorInspection {
  public InvalidPropertyKeyFormInspection() {
    super("InvalidPropertyKeyForm");
  }

  @Override
  protected void checkStringDescriptor(final Module module,
                                       final IComponent component,
                                       final IProperty prop,
                                       final StringDescriptor descriptor,
                                       final FormErrorCollector collector) {
    String error = checkDescriptor(descriptor, module);
    if (error != null) {
      collector.addError(getID(), component, prop, error);
    }
  }

  @Override
  public @Nullable String getAlternativeID() {
    return "UnresolvedPropertyKey";
  }

  private static @Nullable
  @Nls String checkDescriptor(final StringDescriptor descriptor, final Module module) {
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

    if (propFiles.isEmpty()) {
      return UIDesignerBundle.message("inspection.invalid.property.in.form.quickfix.error.bundle.not.found", bundleName);
    }

    for(PropertiesFile propFile: propFiles) {
      final com.intellij.lang.properties.IProperty property = propFile.findPropertyByKey(key);
      if (property == null) {
        return UIDesignerBundle.message("inspection.invalid.property.in.form.quickfix.error.key.not.found",
                                         key, bundleName, propFile.getLocale().getDisplayName());
      }
    }
    return null;
  }
}
