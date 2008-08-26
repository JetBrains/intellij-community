package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerNodeDescriptor extends NodeDescriptor<SMTestProxy>
{
  private SMTestProxy myElement;

  public SMTRunnerNodeDescriptor(final Project project,
                                final SMTestProxy element,
                                final NodeDescriptor<SMTestProxy> parentDesc) {
    super(project, parentDesc);
    myElement = element;
    myName = element.getName();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public SMTestProxy getElement() {
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

