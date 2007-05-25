package com.theoryinpractice.testng.model;

import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TestTreeStructure extends AbstractTreeStructure
{
    private Object root;
    private Filter filter;
    private Project project;

    public TestTreeStructure(Project project, Object root) {
        this.project = project;
        this.root = root;
        filter = Filter.NO_FILTER;
    }

    public void setFilter(Filter filter)
    {
        this.filter = filter;
    }

    public Filter getFilter()
    {
        return filter;
    }

    @Override
    public Object getRootElement()
    {
        return root;
    }

    @Override
    public Object[] getChildElements(Object obj)
    {
        List<AbstractTestProxy> results = ((TestProxy)obj).getResults(filter);
        return results.toArray(new AbstractTestProxy[results.size()]);
    }

    @Override
    public Object getParentElement(Object obj)
    {
        TestProxy node = (TestProxy)obj;
        return node.getParent();
    }

    @NotNull
    @Override
    public TestNodeDescriptor createDescriptor(Object obj, NodeDescriptor parent)
    {
        TestProxy node = (TestProxy)obj;
        return new TestNodeDescriptor(project, node, parent);
    }

    @Override
    public void commit()
    {
    }

    @Override
    public boolean hasSomethingToCommit()
    {
        return false;
    }
}
