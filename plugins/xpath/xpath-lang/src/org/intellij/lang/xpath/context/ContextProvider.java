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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.functions.DefaultFunctionContext;
import org.intellij.lang.xpath.context.functions.FunctionContext;
import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.Set;

public abstract class ContextProvider {
    /**
     * ContextProvider instance, must be available (aka @NotNull) at every XPathFile instance by getCopyableUserData().
     * It gets lost with putUserData()!
     */
    private static final Key<ContextProvider> KEY = Key.create("CONTEXT_PROVIDER");
    private static final Key<Boolean> XML_FILE_WITH_XPATH_INJECTTION = Key.create("XML_FILE_WITH_XPATH_INJECTTION");

    private volatile FunctionContext myFunctionContext;

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
    public FunctionContext getFunctionContext() {
      FunctionContext context = myFunctionContext;
      if (context == null) {
        context = createFunctionContext();
      }
      return (myFunctionContext = context);
    }

    protected FunctionContext createFunctionContext() {
      return DefaultFunctionContext.getInstance(getContextType());
    }

    @NotNull
    public XPathQuickFixFactory getQuickFixFactory() {
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
        if (provider != null && provider.isValid()) {
            return provider;
        }
        final PsiElement context = psiFile.getContext();
        if (context != null) {
            provider = context.getCopyableUserData(KEY);
            if (provider != null && provider.isValid()) {
                return provider;
            }
        }
        return getFromExtensionOrDefault(psiFile);
    }

    protected boolean isValid() {
        final XmlElement contextElement = getContextElement();
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
        return new DefaultProvider(PsiTreeUtil.getContextOfType(psiFile, XmlElement.class, true), psiFile.getLanguage());
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

    public PsiFile[] getRelatedFiles(XPathFile file) {
        return PsiFile.EMPTY_ARRAY;
    }

    @NotNull
    public XPathType getExpectedType(XPathExpression expr) {
        return XPathType.UNKNOWN;
    }

    @Nullable
    public QName getQName(QNameElement element) {
        final PrefixedName qname = element.getQName();
        return qname != null ? getQName(qname, element) : null;
    }

    @Nullable
    public QName getQName(@NotNull PrefixedName qName, XPathElement context) {
        final String prefix = qName.getPrefix();
        final NamespaceContext namespaceContext = getNamespaceContext();
        if (namespaceContext != null) {
            if (prefix != null) {
                final XmlElement element = PsiTreeUtil.getContextOfType(context, XmlElement.class, true);
                final String namespaceURI = namespaceContext.getNamespaceURI(prefix, element);
                return namespaceURI != null && namespaceURI.length() > 0 ? new QName(namespaceURI, qName.getLocalName(), prefix) : null;
            } else if (context.getXPathVersion() == XPathVersion.V2){
              if (isDefaultCapableElement(context)) {
                final String namespace = namespaceContext.getDefaultNamespace(getContextElement());
                if (namespace != null) {
                  return new QName(namespace, qName.getLocalName());
                }
              }
            }
            return new QName(null, qName.getLocalName(), "");
        } else if (qName.getPrefix() == null) {
            return QName.valueOf(qName.getLocalName());
        } else {
            return null;
        }
    }

  private static boolean isDefaultCapableElement(XPathElement context) {
    // http://www.w3.org/TR/xslt20/#unprefixed-qnames
    return (context instanceof XPathNodeTest && ((XPathNodeTest)context).getPrincipalType() == XPathNodeTest.PrincipalType.ELEMENT)
      || context instanceof XPath2TypeElement;
  }

  public static boolean hasXPathInjections(XmlFile file) {
      return Boolean.TRUE.equals(file.getUserData(XML_FILE_WITH_XPATH_INJECTTION));
    }

    public static final class DefaultProvider extends ContextProvider {
      public static NamespaceContext NULL_NAMESPACE_CONTEXT = null;

      private final XmlElement myContextElement;
        private final ContextType myContextType;
        private final NamespaceContext myNamespaceContext;

        DefaultProvider(XmlElement contextElement) {
            myContextElement = contextElement;
            myContextType = ContextType.PLAIN;

            if (myContextElement != null) {
              myNamespaceContext = XsltNamespaceContext.NAMESPACE_CONTEXT;
              setXPathInjected(myContextElement.getContainingFile());
            } else {
              myNamespaceContext = NULL_NAMESPACE_CONTEXT;
            }
        }

        public DefaultProvider(XmlElement element, Language language) {
          myContextElement = element;
          myContextType = language == XPathFileType.XPATH2.getLanguage() ? ContextType.PLAIN_V2 : ContextType.PLAIN;

          if (myContextElement != null) {
            myNamespaceContext = XsltNamespaceContext.NAMESPACE_CONTEXT;
            setXPathInjected(myContextElement.getContainingFile());
          } else {
            myNamespaceContext = NULL_NAMESPACE_CONTEXT;
          }
        }

        private static void setXPathInjected(final PsiFile file) {
          final Boolean flag = file.getUserData(XML_FILE_WITH_XPATH_INJECTTION);

          // This is a very ugly hack, but it is required to make the implicit usages provider recognize namespace declarations used from
          // within injected XPath fragments during IDEA startup. Otherwise, the namespace declarations may be marked as unused until the
          // first edit in the file.
          // Another (possibly perferred) solution might be to make org.intellij.lang.xpath.xslt.impl.XsltImplicitUsagesProvider run
          // unconditionally or - even better - pull its functionality into the platform.
          if (flag == null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (file.getUserData(XML_FILE_WITH_XPATH_INJECTTION) == null) {
                  file.putUserData(XML_FILE_WITH_XPATH_INJECTTION, Boolean.TRUE);
                  // TODO workaround for highlighting tests
                  if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
                    DaemonCodeAnalyzer.getInstance(file.getProject()).restart(file);
                  }
                }
              }
            });
          }
        }

        @NotNull
        public ContextType getContextType() {
            return myContextType;
        }

        @Nullable
        public XmlElement getContextElement() {
            return myContextElement;
        }

        @Nullable
        public NamespaceContext getNamespaceContext() {
          return myNamespaceContext;
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
