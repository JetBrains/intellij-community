package com.jetbrains.python.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
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
    StringBuilder presentableText = new StringBuilder(value.getName());
    if (value instanceof PyFunction) {
      presentableText.append(((PyFunction) value).getParameterList().getPresentableText(false));
    }
    data.setPresentableText(presentableText.toString());
    data.setOpenIcon(value.getIcon(Iconable.ICON_FLAG_OPEN));
    data.setClosedIcon(value.getIcon(Iconable.ICON_FLAG_CLOSED));
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    if (!sortByType) {
      return 0;
    }
    return getValue() instanceof PyClass ? 10 : 20;
  }
}
