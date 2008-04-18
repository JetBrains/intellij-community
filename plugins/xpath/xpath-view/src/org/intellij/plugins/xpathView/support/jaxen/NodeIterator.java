/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.support.jaxen;

import com.intellij.psi.PsiElement;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A generic iterator over PSI nodes.
 *
 * <p>Concrete subclasses must implement the {@link #getFirstNode}
 * and {@link #getNextNode} methods for a specific iteration
 * strategy.</p>
 */
public abstract class NodeIterator implements Iterator {

    public NodeIterator(PsiElement contextNode) {
        node = getFirstNode(contextNode);
        while (!isXPathNode(node))
            node = getNextNode(node);
    }


    /**
     * @see Iterator#hasNext
     */
    public boolean hasNext() {
        return (node != null);
    }


    /**
     * @see Iterator#next
     */
    public Object next() {
        if (node == null)
            throw new NoSuchElementException();
        PsiElement ret = node;
        node = getNextNode(node);
        while (!isXPathNode(node))
            node = getNextNode(node);
        return ret;
    }


    /**
     * @see Iterator#remove
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }


    /**
     * Get the first node for iteration.
     *
     * <p>This method must derive an initial node for iteration
     * from a context node.</p>
     *
     * @param contextNode The starting node.
     * @return The first node in the iteration.
     * @see #getNextNode
     */
    protected abstract PsiElement getFirstNode(PsiElement contextNode);


    /**
     * Get the next node for iteration.
     *
     * <p>This method must locate a following node from the
     * current context node.</p>
     *
     * @param contextNode The current node in the iteration.
     * @return The following node in the iteration, or null
     * if there is none.
     * @see #getFirstNode
     */
    protected abstract PsiElement getNextNode(PsiElement contextNode);


    /**
     * Test whether a node is usable by XPath.
     *
     * @param node The DOM node to test.
     * @return true if the node is usable, false if it should be
     * skipped.
     */
    private boolean isXPathNode(PsiElement node) {
        // null is usable, because it means end
        if (node == null)
            return true;

        // TODO: FIXME
        return true;
    }

    private PsiElement node;
}
