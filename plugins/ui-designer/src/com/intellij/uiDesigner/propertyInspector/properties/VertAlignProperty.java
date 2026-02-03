// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;


public class VertAlignProperty extends AlignProperty {
  public static VertAlignProperty getInstance(Project project) {
    return project.getService(VertAlignProperty.class);
  }

  public VertAlignProperty() {
    super(false);
  }
}
