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

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PreferredSizeProperty extends AbstractDimensionProperty<RadComponent> {
  public static PreferredSizeProperty getInstance(Project project) {
    return ServiceManager.getService(project, PreferredSizeProperty.class);
  }

  public PreferredSizeProperty(){
    super("Preferred Size");
  }

  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myPreferredSize;
  }

  protected void setValueImpl(final RadComponent component, final Dimension value) throws Exception{
    component.getConstraints().myPreferredSize.setSize(value);
  }
}
