package com.theoryinpractice.testng.model;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * @author Hani Suleiman Date: Jul 28, 2005 Time: 10:44:26 PM
 */
public class TestNodeDescriptor extends NodeDescriptor<TestProxy>
{
    private final TestProxy node;

    public TestNodeDescriptor(Project project, TestProxy node, NodeDescriptor<TestProxy> parent) {
        super(project, parent);
        this.node = node;
        myName = node.getName();
    }
    
    @Override
    public boolean update() {
        return false;
    }

    @Override
    public TestProxy getElement() {
        return node;
    }

    public boolean expandOnDoubleClick() {
        return !node.isResult();
    }
}
