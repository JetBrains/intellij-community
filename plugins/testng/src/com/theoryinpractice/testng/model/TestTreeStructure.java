package com.theoryinpractice.testng.model;

import java.util.List;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TestTreeStructure extends AbstractTreeStructure
{
    private Object root;
    private TestFilter filter;
    private Project project;

    public TestTreeStructure(Project project, Object root) {
        this.project = project;
        this.root = root;
        filter = TestFilter.NO_FILTER;
    }

    public void setFilter(TestFilter filter)
    {
        this.filter = filter;
    }

    public TestFilter getFilter()
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
        List<TestProxy> results = ((TestProxy)obj).getResults(filter);
        return results.toArray(new TestProxy[results.size()]);
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
