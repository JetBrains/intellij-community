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

import org.intellij.lang.xpath.psi.impl.ResolveUtil;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

class AttributeReference extends SimpleAttributeReference {
    private final boolean mySoft;
    final ResolveUtil.Matcher myMatcher;

    public AttributeReference(XmlAttribute source, ResolveUtil.Matcher matcher, boolean soft) {
        super(source);
        myMatcher = matcher;
        mySoft = soft;
    }

    @NotNull
    public Object[] getVariants() {
        return ResolveUtil.collect(myMatcher.variantMatcher());
    }

    @Override
    @NotNull
    protected TextRange getTextRange() {
        return TextRange.from(0, myAttribute.getValue().length());
    }

    public boolean isSoft() {
        return mySoft;
    }

    @Override
    protected PsiElement resolveImpl() {
        return ResolveUtil.resolve(myMatcher);
    }
}
