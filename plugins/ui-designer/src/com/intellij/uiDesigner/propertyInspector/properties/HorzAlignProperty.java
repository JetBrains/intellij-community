// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;


public class HorzAlignProperty extends AlignProperty {
  public static HorzAlignProperty getInstance(Project project) {
    return project.getService(HorzAlignProperty.class);
  }

  public HorzAlignProperty() {
    super(true);
  }
}
