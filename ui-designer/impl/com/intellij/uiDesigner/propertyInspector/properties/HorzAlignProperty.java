/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author yole
 */
public class HorzAlignProperty extends AlignProperty {
  public static HorzAlignProperty getInstance(Project project) {
    return ServiceManager.getService(project, HorzAlignProperty.class);
  }

  public HorzAlignProperty() {
    super(true);
  }
}
