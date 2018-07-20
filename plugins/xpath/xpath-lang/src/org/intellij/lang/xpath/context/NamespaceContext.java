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
package org.intellij.lang.xpath.context;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface NamespaceContext {
    @Nullable
    String getNamespaceURI(String prefix, XmlElement context);

    @Nullable
    String getPrefixForURI(String uri, XmlElement context);

    @NotNull
    Collection<String> getKnownPrefixes(XmlElement context);

    /** resolve to NS-Attribute's name-token */
    @Nullable
    PsiElement resolve(String prefix, XmlElement context);

    IntentionAction[] getUnresolvedNamespaceFixes(@NotNull PsiReference reference, String localName);

    @Nullable
    String getDefaultNamespace(XmlElement context);
}
