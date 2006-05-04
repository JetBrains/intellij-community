/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class HorzAlignProperty extends AlignProperty {
  public static HorzAlignProperty getInstance(Project project) {
    return project.getComponent(HorzAlignProperty.class);
  }

  public HorzAlignProperty() {
    super(true);
  }
}
