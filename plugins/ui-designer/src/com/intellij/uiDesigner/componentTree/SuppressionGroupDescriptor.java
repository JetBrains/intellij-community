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
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author yole
 */
public class SuppressionGroupDescriptor extends NodeDescriptor {
  private final LwInspectionSuppression[] myInspectionSuppressions;

  public SuppressionGroupDescriptor(final NodeDescriptor parentDescriptor, final LwInspectionSuppression[] lwInspectionSuppressions) {
    super(null, parentDescriptor);
    myInspectionSuppressions = lwInspectionSuppressions;
  }

  public boolean update() {
    return false;
  }

  public Object getElement() {
    return myInspectionSuppressions;
  }

  @Override public String toString() {
    return UIDesignerBundle.message("node.suppressed.inspections");
  }
}
