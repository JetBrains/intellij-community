// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler;
import org.jetbrains.idea.maven.model.MavenModel;

import java.util.Properties;

public final class Maven40ModelInheritanceAssembler {
  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    // mergeModel_Properties
    if (null != parentModel) {
      Properties parentProperties = parentModel.getProperties();
      Properties properties = model.getProperties();
      for (Object keyObject : parentProperties.keySet()) {
        String key = keyObject.toString();
        if (!properties.containsKey(key)) {
          properties.setProperty(key, parentProperties.getProperty(key));
        }
      }
    }

    Model result = Maven40ModelConverter.toNativeModel(model).getDelegate();
    Model parent = Maven40ModelConverter.toNativeModel(parentModel).getDelegate();
    DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();
    new DefaultInheritanceAssembler().assembleModelInheritance(result, parent, request, new ModelProblemCollector() {
      @Override
      public void add(ModelProblemCollectorRequest request) {
      }
    });
    return Maven40ApiModelConverter.convertModel(result);
  }
}
