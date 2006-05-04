/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class VertAlignProperty extends AlignProperty {
  public static VertAlignProperty getInstance(Project project) {
    return project.getComponent(VertAlignProperty.class);
  }

  public VertAlignProperty() {
    super(false);
  }
}
