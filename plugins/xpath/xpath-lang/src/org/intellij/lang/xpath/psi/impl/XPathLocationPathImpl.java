/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.psi.impl;

import org.intellij.lang.xpath.XPathElementTypes;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathLocationPath;
import org.intellij.lang.xpath.psi.XPathStep;
import org.intellij.lang.xpath.psi.XPathType;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathLocationPathImpl extends XPathElementImpl implements XPathLocationPath {
    public XPathLocationPathImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public XPathType getType() {
        return XPathType.NODESET;
    }


    @Nullable
    public XPathExpression getPathExpression() {
        final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.STEPS);
        assert nodes.length <= 1;
        if (nodes.length > 0) {
            return (XPathExpression)nodes[0].getPsi();
        }
        return null;
    }

    public boolean isAbsolute() {
        final XPathExpression pathExpression = getPathExpression();
        return pathExpression instanceof XPathStep && ((XPathStep)pathExpression).isAbsolute();
    }
}
