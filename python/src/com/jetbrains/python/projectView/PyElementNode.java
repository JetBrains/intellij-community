/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;

import javax.swing.*;
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
      List<AbstractTreeNode> result = new ArrayList<>();
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
    final PyElement value = getValue();
    final String name = value.getName();
    final ItemPresentation presentation = value.getPresentation();
    String presentableText = name != null ? name : PyNames.UNNAMED_ELEMENT;
    Icon presentableIcon = value.getIcon(0);
    if (presentation != null) {
      final String text = presentation.getPresentableText();
      if (text != null) {
        presentableText = text;
      }
      final Icon icon = presentation.getIcon(false);
      if (icon != null) {
        presentableIcon = icon;
      }
    }
    data.setPresentableText(presentableText);
    data.setIcon(presentableIcon);
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
