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

package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;
import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author yole
 */
public class SuppressionDescriptor extends NodeDescriptor {
  private final RadComponent myTarget;
  private final LwInspectionSuppression myInspectionSuppression;

  public SuppressionDescriptor(final NodeDescriptor parentDescriptor, final RadComponent target, final LwInspectionSuppression inspectionSuppression) {
    super(null, parentDescriptor);
    myTarget = target;
    myInspectionSuppression = inspectionSuppression;
  }

  public boolean update() {
    return false;
  }

  public Object getElement() {
    return myInspectionSuppression;
  }

  @Override
  public String toString() {
    StringBuilder titleBuilder = new StringBuilder(myInspectionSuppression.getInspectionId());
    if (myTarget != null) {
      titleBuilder.append(" for ");
      titleBuilder.append(myTarget.getDisplayName());
    }
    return titleBuilder.toString();
  }

  public LwInspectionSuppression getSuppression() {
    return myInspectionSuppression;
  }
}
