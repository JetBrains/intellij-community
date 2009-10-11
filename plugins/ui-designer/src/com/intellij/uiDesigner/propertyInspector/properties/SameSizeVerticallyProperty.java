/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SameSizeVerticallyProperty extends AbstractGridLayoutProperty {
  public static SameSizeVerticallyProperty getInstance(Project project) {
    return ServiceManager.getService(project, SameSizeVerticallyProperty.class);
  }

  public SameSizeVerticallyProperty(){
    super(null,"Same Size Vertically");
  }

  protected boolean getGridLayoutPropertyValue(final GridLayoutManager gridLayoutManager) {
    return gridLayoutManager.isSameSizeVertically();
  }

  protected void setGridLayoutPropertyValue(final GridLayoutManager gridLayoutManager, final boolean booleanValue) {
    gridLayoutManager.setSameSizeVertically(booleanValue);
  }
}
