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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestTreeViewStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TestTreeStructure extends TestTreeViewStructure<TestProxy>
{
    private final Object root;
    private final Project project;

    public TestTreeStructure(Project project, Object root) {
        this.project = project;
        this.root = root;
    }

    @Override
    public Object getRootElement()
    {
        return root;
    }

    @Override
    public Object[] getChildElements(Object obj)
    {
        List<AbstractTestProxy> results = ((TestProxy)obj).getResults(getFilter());
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
