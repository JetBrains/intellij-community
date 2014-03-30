/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyElementNode extends BasePsiNode<PyElement> {
  public PyElementNode(Project project, PyElement value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    PyElement value = getValue();
    // for performance reasons, we don't show nested functions here
    if (value instanceof PyClass) {
      final PyClass pyClass = (PyClass)value;
      List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (PyClass aClass : pyClass.getNestedClasses()) {
        result.add(new PyElementNode(myProject, aClass, getSettings()));
      }
      for (PyFunction function : pyClass.getMethods()) {
        result.add(new PyElementNode(myProject, function, getSettings()));
      }
      return result;
    }
    return Collections.emptyList();       
  }

  @Override
  protected void updateImpl(PresentationData data) {
    PyElement value = getValue();
    final String name = value.getName();
    StringBuilder presentableText = new StringBuilder(name != null ? name : "<unnamed>");
    if (value instanceof PyFunction) {
      presentableText.append(((PyFunction) value).getParameterList().getPresentableText(false));
    }
    data.setPresentableText(presentableText.toString());
    data.setIcon(value.getIcon(0));
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    if (!sortByType) {
      return 0;
    }
    return getValue() instanceof PyClass ? 10 : 20;
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }
}
