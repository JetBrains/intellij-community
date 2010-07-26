/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.theoryinpractice.testng.model;

import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author Hani Suleiman Date: Jul 28, 2005 Time: 10:49:36 PM
 */
public class TestTreeBuilder extends AbstractTestTreeBuilder
{
    public TestTreeBuilder(JTree tree, AbstractTreeStructure structure) {
        super(tree, new DefaultTreeModel(new DefaultMutableTreeNode(structure.getRootElement())), structure, IndexComparator.INSTANCE);
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

    @Override
    protected boolean isAutoExpandNode(NodeDescriptor descriptor) {
        return descriptor.getElement() == getTreeStructure().getRootElement();
    }

    public void addItem(TestProxy parent, TestProxy proxy) {
        parent.addChild(proxy);
        DefaultMutableTreeNode parentNode = getNodeForElement(parent);
        if (parentNode != null)
            updateSubtree(parentNode);
    }

    public TestProxy getRoot() {
        return (TestProxy) getTreeStructure().getRootElement();
    }
}
