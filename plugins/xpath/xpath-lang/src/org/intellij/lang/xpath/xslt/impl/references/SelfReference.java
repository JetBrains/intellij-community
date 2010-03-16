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

package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class SelfReference implements PsiReference {
    private final XmlAttributeValue myValue;
    private final PsiElement myTarget;

    public SelfReference(XmlAttribute element, PsiElement target) {
        this.myTarget = target;
        this.myValue = element.getValueElement();
    }

    public PsiElement getElement() {
        return myValue;
    }

    public TextRange getRangeInElement() {
        return TextRange.from(1, myValue.getTextLength() - 2);
    }

    @Nullable
    public PsiElement resolve() {
        return myValue.isValid() ? myTarget : null;
    }

    public String getCanonicalText() {
        return myValue.getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        return myValue;
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        return myValue;
    }

    public boolean isReferenceTo(PsiElement element) {
        return false;
    }

    @NotNull
    public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
        return false;
    }
}
