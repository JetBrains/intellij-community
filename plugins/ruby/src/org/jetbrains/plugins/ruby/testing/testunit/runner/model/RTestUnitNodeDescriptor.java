package org.jetbrains.plugins.ruby.testing.testunit.runner.model;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitNodeDescriptor extends NodeDescriptor<RTestUnitTestProxy>
{
  private RTestUnitTestProxy myElement;

  public RTestUnitNodeDescriptor(final Project project,
                                final RTestUnitTestProxy element,
                                final NodeDescriptor<RTestUnitTestProxy> parentDesc) {
    super(project, parentDesc);
    myElement = element;
    myName = element.getName();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public RTestUnitTestProxy getElement() {
    return myElement;
  }

  public boolean expandOnDoubleClick() {
    return !myElement.isLeaf();
  }

  @Override
  public String toString() {
    return myName;
  }
}

