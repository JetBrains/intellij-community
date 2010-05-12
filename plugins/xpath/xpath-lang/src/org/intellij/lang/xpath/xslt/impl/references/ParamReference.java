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

import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

class ParamReference extends AttributeReference implements PsiPolyVariantReference {
    public ParamReference(XmlAttribute source, ResolveUtil.Matcher matcher) {
        super(source, matcher, true);
    }

    @Override
    protected PsiElement resolveImpl() {
        final ResolveResult[] results = multiResolve(false);
        return results.length == 1 ? results[0].getElement() : null;
    }

    @NotNull
    public final ResolveResult[] multiResolve(final boolean incompleteCode) {
      return PsiElementResolveResult.createResults(ResolveUtil.collect(myMatcher));
    }
}
