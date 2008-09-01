/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.support;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.plugins.xpathView.util.Namespace;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class XPathSupport {
    public static final ContextType TYPE = ContextType.lookupOrCreate("INTERACTIVE");

    public abstract XPath createXPath(@NotNull XmlFile file, String expression) throws JaxenException;

    public abstract XPath createXPath(@Nullable XmlFile psiFile, String expression, @NotNull Collection<Namespace> namespaces) throws JaxenException;

    public abstract String getPath(XmlElement element, XmlTag context);

    public abstract String getUniquePath(XmlElement element, XmlTag context);

    public static XPathSupport getInstance() {
        return ServiceManager.getService(XPathSupport.class);
    }
}
