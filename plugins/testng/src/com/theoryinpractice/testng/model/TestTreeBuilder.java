package com.theoryinpractice.testng.model;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author Hani Suleiman Date: Jul 28, 2005 Time: 10:49:36 PM
 */
public class TestTreeBuilder extends AbstractTreeBuilder
{
    public TestTreeBuilder(JTree tree, AbstractTreeStructure structure) {
        super(tree, new DefaultTreeModel(new DefaultMutableTreeNode(structure.getRootElement())), structure, IndexComparator.INSTANCE);
        tree.setModel(getTreeModel());
        initRootNode();
    }

    @Override
    protected boolean isSmartExpand() {
        return false;
    }

    @Override
    protected boolean isAlwaysShowPlus(NodeDescriptor descriptor) {
        return false;
    }

    public void repaintWithParents(TestProxy proxy) {
        TestProxy current = proxy;
        do {
            DefaultMutableTreeNode node = getNodeForElement(current);
            if (node != null) {
                JTree tree = getTree();
                ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
            }
            current = current.getParent();
        }
        while (current != null);
    }

    @Override
    protected boolean isAutoExpandNode(NodeDescriptor descriptor) {
        return descriptor.getElement() == getTreeStructure().getRootElement();
    }

    public void addItem(TestProxy parent, TestProxy proxy) {
        parent.addResult(proxy);
        DefaultMutableTreeNode parentNode = getNodeForElement(parent);
        if (parentNode != null)
            updateSubtree(parentNode);
    }

    public DefaultMutableTreeNode ensureTestVisible(TestProxy proxy) {
        DefaultMutableTreeNode node = getNodeForElement(proxy);
        if (node != null) {
            if (node.getParent() != null) {
                expandNodeChildren((DefaultMutableTreeNode) node.getParent());
                node = getNodeForElement(proxy);
            }
            return node;
        }
        TestProxy path[] = proxy.getPathFromRoot();
        for (TestProxy item : path) {
            buildNodeForElement(item);
            node = getNodeForElement(item);
            if (node == null) return null;
            expandNodeChildren(node);
        }

        return node;
    }

    public TestProxy getRoot() {
        return (TestProxy) getTreeStructure().getRootElement();
    }
}
