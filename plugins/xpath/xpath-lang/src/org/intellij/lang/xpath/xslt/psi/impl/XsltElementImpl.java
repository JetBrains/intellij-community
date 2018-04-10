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
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.completion.CompletionLists;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.XPathElementVisitor;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.*;

abstract class XsltElementImpl extends LightElement implements Iconable, PsiElementNavigationItem, XsltElement, ItemPresentation {

    protected final @NotNull XmlTag myElement;
    protected final XsltElementFactory myElementFactory;

    private final int myHashCode;
    private PsiElement myNavigationElement;

    protected XsltElementImpl(XmlTag target) {
        super(target.getManager(), XsltLanguage.INSTANCE);
        myElement = target;
        myHashCode = myElement.hashCode();
        myElementFactory = XsltElementFactory.getInstance();
    }

    public PsiElement copy() {
        return myElementFactory.wrapElement((XmlTag)myElement.copy(), getClass());
    }

    public String getText() {
        return myElement.getText();
    }

    public XmlTag getTag() {
        return myElement;
    }

    @Override
    @Nullable
    public final ItemPresentation getPresentation() {
        return this;
    }

    @Nullable
    public Icon getIcon(boolean open) {
        return getIcon(0);
    }

    @Nullable
    public String getLocationString() {
        return "(in " + getContainingFile().getName() + ")";
    }

    @SuppressWarnings({"ConstantConditions"})
    public String getPresentableText() {
        return getName();
    }

    @Override
    public PsiElement getTargetElement() {
        return myElement;
    }

    @Nullable
    public String getName() {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.getValue() : null;
    }

    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        assert myElement.isValid();

        myElement.setAttribute("name", name);
        return this;
    }

    @NotNull
    @Override
    @SuppressWarnings({ "RawUseOfParameterizedType" })
    public PsiElement getNavigationElement() {
        if (myNavigationElement == null && myElement.isValid()) {
            final Class[] allInterfaces = CompletionLists.getAllInterfaces(myElement.getClass());
            myNavigationElement = (PsiElement)Proxy.newProxyInstance(getClass().getClassLoader(), allInterfaces, new InvocationHandler() {
                @Nullable
                @SuppressWarnings({"StringEquality", "AutoBoxing", "AutoUnboxing"})
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    try {
                        final XmlAttributeValue nameElement = XsltElementImpl.this.getNameElement();
                        if (method.getName() == "navigate") {
                            assert nameElement != null;

                            ((NavigationItem)nameElement).navigate((Boolean)args[0]);

                            return null;
                        } else if (method.getName() == "canNavigate") {
                            return nameElement instanceof NavigationItem && ((NavigationItem)nameElement).canNavigate();
                        } else if (method.getName() == "getTextOffset") {
                            return nameElement != null ? nameElement.getTextOffset() : myElement.getTextOffset();
                        }
                        return method.invoke(myElement, args);
                    } catch (InvocationTargetException e1) {
                        throw e1.getTargetException();
                    }
                }
            });
        }
        return myElement.isValid() ? myNavigationElement : this;
    }

    @Nullable
    public XmlAttribute getNameAttribute() {
        return myElement.getAttribute("name", null);
    }

    @Nullable
    public PsiElement getNameIdentifier() {
        final XmlAttribute nameAttribute = getNameAttribute();
        return nameAttribute != null ? XsltSupport.getAttValueToken(nameAttribute) : null;
    }

    @Nullable
    private XmlAttributeValue getNameElement() {
        final XmlAttribute attribute = getNameAttribute();
        if (attribute != null) {
            final XmlAttributeValue valueElement = attribute.getValueElement();
            return valueElement != null ? valueElement : null;
        }
        return null;
    }

    @Override
    public PsiElement getOriginalElement() {
        return myElement.getOriginalElement();
    }

    @Override
    public boolean isValid() {
        return myElement.isValid();
    }

    @Override
    @NotNull
    public Language getLanguage() {
        return XsltLanguage.INSTANCE;
    }

    @Override
    public void navigate(boolean b) {
        final XmlAttributeValue nameElement = getNameElement();
        assert nameElement != null;
        ((NavigationItem)nameElement).navigate(b);
    }

    @Override
    public boolean canNavigate() {
        final XmlAttributeValue nameElement = getNameElement();
        return myElement.isValid() && nameElement instanceof NavigationItem && ((NavigationItem)nameElement).canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    public void accept(@NotNull XPathElementVisitor visitor) {
      visitor.visitXPathElement((XPathElement)this);
    }

    public void accept(@NotNull PsiElementVisitor visitor) {
      if (visitor instanceof XPathElementVisitor && this instanceof XPathElement) {
        accept((XPathElementVisitor)visitor);
      } else {
        myElement.accept(visitor);
      }
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final XsltElementImpl that = (XsltElementImpl)o;

        return myElement.equals(that.myElement);
    }

    @Override
    public PsiFile getContainingFile() {
        return myElement.getContainingFile();
    }

    @Override
    public boolean isWritable() {
        return myElement.isWritable();
    }

    @Override
    public PsiElement getParent() {
//        final XmlTag parent = PsiTreeUtil.getParentOfType(myElement.getParent(), XmlTag.class);
//        return parent != null ?
//                XsltSupport.isXsltTag(parent) ? myElementFactory.wrapElement(parent) : parent
//                : null;

        return myElement.getParent(); // TODO: return XSLT object
    }

    @Override
    @NotNull
    public PsiElement[] getChildren() {
        return myElement.getChildren(); // TODO: return XSLT objects
    }

    @Override
    public TextRange getTextRange() {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.getTextRange() : myElement.getTextRange();
    }

    @Override
    public int getStartOffsetInParent() {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.getStartOffsetInParent() : myElement.getStartOffsetInParent();
    }

    @Override
    @NotNull
    public char[] textToCharArray() {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.textToCharArray() : myElement.textToCharArray();
    }

    @Override
    public int getTextOffset() {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.getTextOffset() : myElement.getTextOffset();
    }

    @Override
    public ASTNode getNode() {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.getNode() : myElement.getNode();
    }

    @Override
    @NotNull
    public PsiReference[] getReferences() {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.getReferences() : myElement.getReferences();
    }

    @Override
    public boolean textMatches(@NotNull CharSequence charSequence) {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.textMatches(charSequence) : myElement.textMatches(charSequence);
    }

    @Override
    public boolean textMatches(@NotNull PsiElement psiElement) {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.textMatches(psiElement) : myElement.textMatches(psiElement);
    }

    @Override
    public PsiElement findElementAt(int i) {
        final XmlAttributeValue nameElement = getNameElement();
        return nameElement != null ? nameElement.findElementAt(i) : myElement.findElementAt(i);
    }

    @Override
    public void delete() throws IncorrectOperationException {
        myElement.delete();
    }

    protected static <S, T extends S> T[] convertArray(S[] elements, Class<T> aClass) {
        //noinspection unchecked
        final T[] t = (T[])Array.newInstance(aClass, elements.length);
        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(elements, 0, t, 0, elements.length);
        return t;
    }

  public ContextProvider getXPathContext() {
    throw new UnsupportedOperationException();
  }

  public XPathVersion getXPathVersion() {
    throw new UnsupportedOperationException();
  }
}
