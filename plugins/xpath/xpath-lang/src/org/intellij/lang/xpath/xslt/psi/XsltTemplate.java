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
package org.intellij.lang.xpath.xslt.psi;

import org.intellij.lang.xpath.psi.XPathExpression;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;

public interface XsltTemplate extends PsiNamedElement, XsltNamedElement {
    @NotNull
    XsltParameter[] getParameters();

    @Nullable
    XsltParameter getParameter(String name);

    @Nullable
    XPathExpression getMatchExpression();

    @Nullable
    QName getMode();

    /**
     * Custom extension to define abstract templates 
     */
    boolean isAbstract();
}