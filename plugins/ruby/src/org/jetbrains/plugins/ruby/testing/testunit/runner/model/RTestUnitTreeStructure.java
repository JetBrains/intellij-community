package org.jetbrains.plugins.ruby.testing.testunit.runner.model;

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
public class RTestUnitTreeStructure  extends AbstractTreeStructure
{
  private Object myRootNode;
  private Filter myTestNodesFilter;
  private Project myProject;

  public RTestUnitTreeStructure(final Project project, final Object rootNode) {
    myProject = project;
    myRootNode = rootNode;
    myTestNodesFilter = Filter.NO_FILTER;
  }

  @Override
  public void commit() {
  }

  @NotNull
  @Override
  public RTestUnitNodeDescriptor createDescriptor(final Object element,
                                                  final NodeDescriptor parentDesc) {
    //noinspection unchecked
    return new RTestUnitNodeDescriptor(myProject,
                                       (RTestUnitTestProxy)element,
                                       (NodeDescriptor<RTestUnitTestProxy>)parentDesc);
  }

  public Filter getFilter() {
    return myTestNodesFilter;
  }

  @Override
  public Object[] getChildElements(final Object element) {
    final List<? extends RTestUnitTestProxy> results =
        ((RTestUnitTestProxy)element).getChildren(myTestNodesFilter);

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
