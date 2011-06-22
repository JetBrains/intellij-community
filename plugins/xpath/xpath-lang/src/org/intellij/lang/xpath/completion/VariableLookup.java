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
package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.lookup.LookupValueWithPriority;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

public class VariableLookup extends AbstractLookup implements Lookup, Iconable, LookupValueWithPriority, ElementProvider {
    private final String myType;
    private final Icon myIcon;
    private final PsiElement myPsiElement;

    public VariableLookup(String name, Icon icon) {
        this(name, "", icon, null);
    }

    public VariableLookup(String name, String type, Icon icon, PsiElement psiElement) {
        super(name, name);
        myType = type;
        myIcon = icon;
        myPsiElement = psiElement;
    }

    public String getTypeHint() {
        return myType;
    }

    public Icon getIcon(int flags) {
        return myIcon != null ? myIcon : PlatformIcons.VARIABLE_ICON;
    }

    public int getPriority() {
        return HIGH;
    }

    public PsiElement getElement() {
        return myPsiElement;
    }
}
