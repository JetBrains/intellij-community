package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerTreeStructure extends AbstractTreeStructure
{
  private Object myRootNode;
  private Filter myTestNodesFilter;
  private Project myProject;

  public SMTRunnerTreeStructure(final Project project, final Object rootNode) {
    myProject = project;
    myRootNode = rootNode;
    myTestNodesFilter = Filter.NO_FILTER;
  }

  @Override
  public void commit() {
  }

  @NotNull
  @Override
  public SMTRunnerNodeDescriptor createDescriptor(final Object element,
                                                  final NodeDescriptor parentDesc) {
    //noinspection unchecked
    return new SMTRunnerNodeDescriptor(myProject,
                                       (SMTestProxy)element,
                                       (NodeDescriptor<SMTestProxy>)parentDesc);
  }

  public Filter getFilter() {
    return myTestNodesFilter;
  }

  @Override
  public Object[] getChildElements(final Object element) {
    final List<? extends SMTestProxy> results =
        ((SMTestProxy)element).getChildren(myTestNodesFilter);

    return results.toArray(new AbstractTestProxy[results.size()]);
  }

  @Override
  public Object getParentElement(final Object element) {
    return ((AbstractTestProxy)element).getParent();
  }

  @Override
  public Object getRootElement() {
    return myRootNode;
  }


  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public void setFilter(final Filter nodesFilter) {
    myTestNodesFilter = nodesFilter;
  }
}
