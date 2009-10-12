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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;

/**
 * @author yole
 */
public class UseParentLayoutProperty extends AbstractBooleanProperty<RadComponent> {
  public static UseParentLayoutProperty getInstance(Project project) {
    return ServiceManager.getService(project, UseParentLayoutProperty.class);
  }

  public UseParentLayoutProperty() {
    super(null, "Align Grid with Parent", false);
  }

  public Boolean getValue(RadComponent component) {
    return component.getConstraints().isUseParentLayout();
  }

  protected void setValueImpl(RadComponent component, Boolean value) throws Exception {
    final boolean useParentLayout = value.booleanValue();

    final GridConstraints constraints = component.getConstraints();
    if (constraints.isUseParentLayout() != useParentLayout) {
      GridConstraints oldConstraints = (GridConstraints)constraints.clone();
      constraints.setUseParentLayout(useParentLayout);
      component.fireConstraintsChanged(oldConstraints);
    }
  }
  @Override public boolean appliesTo(RadComponent component) {
    return component instanceof RadContainer && ((RadContainer)component).getLayoutManager().isGrid() && component.getParent().getLayoutManager().isGrid();
  }
}
