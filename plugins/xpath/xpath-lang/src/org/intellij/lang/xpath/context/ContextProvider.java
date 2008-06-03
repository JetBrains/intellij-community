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

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.context.functions.DefaultFunctionContext;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.FunctionContext;
import org.intellij.lang.xpath.psi.PrefixedName;
import org.intellij.lang.xpath.psi.QNameElement;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.psi.XPathFunction;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;

import javax.xml.namespace.QName;
import java.util.Set;

public abstract class ContextProvider {
    /**
     * ContextProvider instance, must be available (aka @NotNull) at every XPathFile instance by getCopyableUserData().
     * It gets lost with putUserData()!
     */
    private static final Key<ContextProvider> KEY = Key.create("CONTEXT_PROVIDER");

    private DefaultFunctionContext myFunctionContext;

    protected ContextProvider() {
    }

    @NotNull
    public abstract ContextType getContextType();

    @Nullable
    public abstract XmlElement getContextElement();

    @Nullable
    public abstract NamespaceContext getNamespaceContext();

    @Nullable
    public abstract VariableContext getVariableContext();

    @NotNull
    public synchronized FunctionContext getFunctionContext() {
        if (myFunctionContext == null) {
            myFunctionContext = DefaultFunctionContext.getInstance(getContextType());
        }
        return myFunctionContext;
    }

    @NotNull
    public synchronized XPathQuickFixFactory getQuickFixFactory() {
        return XPathQuickFixFactoryImpl.INSTANCE;
    }

    @Nullable
    public abstract Set<QName> getAttributes(boolean forValidation);

    @Nullable
    public abstract Set<QName> getElements(boolean forValidation);

    public void attachTo(PsiFile file) {
        assert file instanceof XPathFile;
        file.putCopyableUserData(KEY, this);
    }

    protected final void attachTo(XmlElement context) {
        context.putCopyableUserData(KEY, this);
    }

    @SuppressWarnings({ "ClassReferencesSubclass" })
    public static void copy(@NotNull PsiFile file1, @NotNull XPathFile file2) {
        final ContextProvider contextProvider = getContextProvider(file1);
        if (!(contextProvider instanceof DefaultProvider)) {
            contextProvider.attachTo(file2);
        }
    }

    @NotNull
    public static ContextProvider getContextProvider(PsiFile psiFile) {
        ContextProvider provider = psiFile.getCopyableUserData(KEY);
        if (provider != null && isValid(provider)) {
            return provider;
        }
        final PsiElement context = psiFile.getContext();
        if (context != null) {
            provider = context.getCopyableUserData(KEY);
            if (provider != null && isValid(provider)) {
                return provider;
            }
        }
        return getFromExtensionOrDefault(psiFile);
    }

    private static boolean isValid(ContextProvider provider) {
        final XmlElement contextElement = provider.getContextElement();
        return contextElement != null && contextElement.isValid();
    }

    @SuppressWarnings({ "ClassReferencesSubclass" })
    private static ContextProvider getFromExtensionOrDefault(PsiFile psiFile) {
        if (psiFile instanceof XPathFile) {
            final ContextProvider instance = ContextProviderExtension.getInstance((XPathFile)psiFile);
            if (instance != null) {
                instance.attachTo(psiFile);
                return instance;
            }
        }
        return new DefaultProvider(PsiTreeUtil.getContextOfType(psiFile, XmlElement.class, true));
    }

    @SuppressWarnings({ "ClassReferencesSubclass" })
    @NotNull
    public static ContextProvider getContextProvider(PsiElement element) {
        return element instanceof XPathElement ?
                getContextProvider(element instanceof XPathFile ?
                        (PsiFile)element :
                        element.getContainingFile()) :
                new DefaultProvider(PsiTreeUtil.getParentOfType(element, XmlElement.class, false));
    }

    @NotNull
    public XPathType getFunctionType(XPathFunctionCall call) {
        final XPathFunction f = call.resolve();
        if (f == null) return XPathType.UNKNOWN;
        final Function function = f.getDeclaration();
        return function != null ? function.returnType : XPathType.UNKNOWN;
    }

    public PsiFile[] getRelatedFiles(XPathFile file) {
        return PsiFile.EMPTY_ARRAY;
    }

    @NotNull
    public XPathType getExpectedType(XPathExpression expr) {
        return XPathType.UNKNOWN;
    }

    @Nullable
    public QName getQName(QNameElement element) {
        return getQName(element.getQName(), element);
    }

    @Nullable
    public QName getQName(PrefixedName qName, XPathElement context) {
        final String prefix = qName.getPrefix();
        final NamespaceContext namespaceContext = getNamespaceContext();
        if (namespaceContext != null) {
            if (prefix != null) {
                final XmlElement element = PsiTreeUtil.getContextOfType(context, XmlElement.class, true);
                final String namespaceURI = namespaceContext.getNamespaceURI(prefix, element);
                return namespaceURI != null && namespaceURI.length() > 0 ? new QName(namespaceURI, qName.getLocalName(), prefix) : null;
            } else {
                return new QName(null, qName.getLocalName(), "");
            }
        } else if (qName.getPrefix() == null) {
            return QName.valueOf(qName.getLocalName());
        } else {
            return null;
        }
    }

    @Nullable
    public RefactoringSupportProvider getRefactoringSupportProvider() {
        return null;
    }

    static class DefaultProvider extends ContextProvider {
        private final XmlElement myContextElement;

        DefaultProvider(XmlElement contextElement) {
            myContextElement = contextElement;
        }

        @NotNull
        public ContextType getContextType() {
            return ContextType.PLAIN;
        }

        @Nullable
        public XmlElement getContextElement() {
            return myContextElement;
        }

        @Nullable
        public NamespaceContext getNamespaceContext() {
            return null;
        }

        @Nullable
        public VariableContext getVariableContext() {
            return null;
        }

        @Nullable
        public Set<QName> getAttributes(boolean forValidation) {
            return null;
        }

        @Nullable
        public Set<QName> getElements(boolean forValidation) {
            return null;
        }
    }
}
