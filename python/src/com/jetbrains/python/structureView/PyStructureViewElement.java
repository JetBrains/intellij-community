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

package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.06.2005
 * Time: 13:32:20
 * To change this template use File | Settings | File Templates.
 */
public class PyStructureViewElement implements StructureViewTreeElement {
    private PyElement _element;

    public PyStructureViewElement(PyElement element) {
        _element = element;
    }

    public PyElement getValue() {
        return _element;
    }

    public void navigate(boolean requestFocus) {
        ((NavigationItem) _element).navigate(requestFocus);
    }

    public boolean canNavigate() {
        return ((NavigationItem) _element).canNavigate();
    }

    public boolean canNavigateToSource() {
        return ((NavigationItem) _element).canNavigateToSource();
    }

    public StructureViewTreeElement[] getChildren() {
        final List<PyElement> childrenElements = new ArrayList<PyElement>();
        _element.acceptChildren(new PyElementVisitor() {
            @Override public void visitElement(PsiElement element) {
                if (element instanceof PsiNamedElement && ((PsiNamedElement)element).getName() != null) {
                  childrenElements.add((PyElement)element);
                }
                else {
                  element.acceptChildren(this);
                }
            }

            @Override public void visitPyParameter(final PyParameter node) {
                // Do not add parameters to structure view
            }
        });

        StructureViewTreeElement[] children = new StructureViewTreeElement[childrenElements.size()];
        for (int i = 0; i < children.length; i++) {
          children[i] = new PyStructureViewElement(childrenElements.get(i));
        }

        return children;
    }

    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            public String getPresentableText() {
                if (_element instanceof PyFunction) {
                    PsiElement[] children = _element.getChildren();
                    if (children.length > 0 && children [0] instanceof PyParameterList) {
                        PyParameterList argList = (PyParameterList) children [0];
                        StringBuilder result = new StringBuilder(((PsiNamedElement) _element).getName());
                        result.append("(");
                        boolean first = true;
                        for(PsiElement e: argList.getChildren()) {
                            if (e instanceof PyParameter) {
                                if (first) {
                                    first = false;
                                }
                                else {
                                    result.append(",");
                                }
                                PyParameter p = (PyParameter) e;
                                if (p.isPositionalContainer()) {
                                    result.append("*");
                                }
                                else if (p.isKeywordContainer()) {
                                    result.append("**");
                                }
                                result.append(p.getName());
                            }
                        }
                        result.append(")");
                        return result.toString();
                    }
                }
                return ((PsiNamedElement) _element).getName();
            }

            public @Nullable TextAttributesKey getTextAttributesKey() {
                return null;
            }

            public @Nullable String getLocationString() {
                return null;
            }

            public Icon getIcon(boolean open) {
                return _element.getIcon(Iconable.ICON_FLAG_OPEN);
            }
        };
    }
}
