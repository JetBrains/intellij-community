/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties.references;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileProcessor;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.List;
import java.util.Set;

public class PropertiesPsiCompletionUtil {
  public static void addVariantsFromFile(PropertyReferenceBase propertyReference,
                                         final PropertiesFile propertiesFile,
                                         final Set<Object> variants) {
    if (propertiesFile == null) return;
    if (!ProjectRootManager.getInstance(propertiesFile.getProject()).getFileIndex().isInContent(propertiesFile.getVirtualFile())) return;
    List<? extends IProperty> properties = propertiesFile.getProperties();
    for (IProperty property : properties) {
      propertyReference.addKey(property, variants);
    }
  }

  static Set<Object> getPropertiesKeys(final PropertyReferenceBase propertyReference) {
    final Set<Object> variants = new THashSet<>(new TObjectHashingStrategy<Object>() {
      public int computeHashCode(final Object object) {
        if (object instanceof IProperty) {
          final String key = ((IProperty)object).getKey();
          return key == null ? 0 : key.hashCode();
        }
        else {
          return 0;
        }
      }

      public boolean equals(final Object o1, final Object o2) {
        return o1 instanceof IProperty && o2 instanceof IProperty &&
               Comparing.equal(((IProperty)o1).getKey(), ((IProperty)o2).getKey(), true);
      }
    });
    List<PropertiesFile> propertiesFileList = propertyReference.getPropertiesFiles();
    if (propertiesFileList == null) {
      PropertiesReferenceManager
        .getInstance(propertyReference.getElement().getProject()).processAllPropertiesFiles((baseName, propertiesFile) -> {
          addVariantsFromFile(propertyReference, propertiesFile, variants);
          return true;
        });
    }
    else {
      for (PropertiesFile propFile : propertiesFileList) {
        addVariantsFromFile(propertyReference, propFile, variants);
      }
    }
    return variants;
  }
}
