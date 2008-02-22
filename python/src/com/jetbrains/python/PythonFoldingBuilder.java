/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.06.2005
 * Time: 22:21:09
 * To change this template use File | Settings | File Templates.
 */
public class PythonFoldingBuilder implements FoldingBuilder {
    public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
        List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
        appendDescriptors(node, descriptors);
        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }

    private static void appendDescriptors(ASTNode node,
                                          List<FoldingDescriptor> descriptors) {
        if (node.getElementType() == PyElementTypes.STATEMENT_LIST) {
            IElementType elType = node.getTreeParent().getElementType();
            if (elType == PyElementTypes.FUNCTION_DECLARATION || elType == PyElementTypes.CLASS_DECLARATION) {
                ASTNode colon = node.getTreeParent().findChildByType(PyTokenTypes.COLON);
                if (colon != null) {
                    descriptors.add(new FoldingDescriptor(node,
                            new TextRange(colon.getStartOffset() + 1, node.getStartOffset() + node.getTextLength())));
                }
                else {
                    descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
                }
            }
        }

        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            appendDescriptors(child, descriptors);
            child = child.getTreeNext();
        }
    }

    public String getPlaceholderText(ASTNode node) {
        return "...";
    }

    public boolean isCollapsedByDefault(ASTNode node) {
        return false;
    }
}
