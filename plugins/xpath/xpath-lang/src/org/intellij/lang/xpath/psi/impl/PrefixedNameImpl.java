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

import org.intellij.lang.xpath.psi.PrefixedName;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixedNameImpl implements PrefixedName {
    private final ASTNode prefixNode;
    private final ASTNode localNode;

    PrefixedNameImpl(@Nullable ASTNode prefixNode, @NotNull ASTNode localNode) {
        this.prefixNode = prefixNode;
        this.localNode = localNode;
    }

    public PrefixedNameImpl(@NotNull ASTNode node) {
        this(null, node);
    }

    public String getPrefix() {
        return prefixNode != null ? prefixNode.getText() : null;
    }

    @NotNull
    public String getLocalName() {
        return localNode.getText();
    }

    public ASTNode getPrefixNode() {
        return prefixNode;
    }

    public ASTNode getLocalNode() {
        return localNode;
    }

    public String toString() {
        return prefixText() + localNode.getText();
    }

    private String prefixText() {
        return prefixNode != null ? prefixNode.getText() + ":" : "";
    }

    public boolean equals(Object object) {
        return object.getClass() == getClass() && ((PrefixedName)object).getLocalName().equals(getLocalName());
    }

    public int hashCode() {
        return getLocalName().hashCode();
    }
}
