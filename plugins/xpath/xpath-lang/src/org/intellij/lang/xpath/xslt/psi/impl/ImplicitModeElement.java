package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import icons.XpathIcons;
import org.intellij.lang.xpath.completion.CompletionLists;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.intellij.lang.xpath.xslt.impl.references.PrefixReference;
import org.intellij.lang.xpath.xslt.util.QNameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.namespace.QName;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ImplicitModeElement extends LightElement implements PsiNamedElement, NavigationItem, ItemPresentation {
    private final XmlAttribute myAttribute;
    private PsiElement myNavigationElement;

    public ImplicitModeElement(XmlAttribute attribute) {
        super(attribute.getManager(), XsltLanguage.INSTANCE);
        myAttribute = attribute;
    }

    @Nullable
    public QName getQName() {
        final String prefix = getPrefix();
        if (prefix != null && prefix.length() > 0) {
            final String uri = XsltNamespaceContext.getNamespaceUriStatic(prefix, myAttribute);
            return uri != null && uri.length() > 0 ? new QName(uri, getName(), prefix) : QNameUtil.UNRESOLVED;
        } else {
            return new QName(getName());
        }
    }

    @Nullable
    private String getPrefix() {
        return hasPrefix() ? PrefixReference.getPrefixRange(myAttribute).substring(myAttribute.getValue()) : null;
    }

    @Override
    public Icon getIcon(int i) {
        return XpathIcons.Template;
    }

    public String getName() {
        return getModeRange().substring(myAttribute.getValue());
    }

    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        // name is calculated dynamically from attached attribute. actual renaming is done by each reference
        return this;
    }

    @Override
    public String toString() {
        return "Mode: " + getName();
    }

    public void accept(@NotNull PsiElementVisitor visitor) {
    }

    public PsiElement copy() {
        return this;
    }

    @SuppressWarnings({"ConstantConditions"})
    public String getText() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ImplicitModeElement that = (ImplicitModeElement)o;

        return QNameUtil.equal(getQName(), that.getQName());
    }

    @Override
    public int hashCode() {
        final QName qName = getQName();
        return qName != null ? qName.hashCode() : 0;
    }

    @Override
    public boolean isPhysical() {
        return myAttribute.isPhysical();
    }

    @Override
    public boolean isWritable() {
        return myAttribute.isWritable();
    }

    public boolean isValid() {
        return myAttribute.isValid();
    }

    @Override
    public int getTextOffset() {
        final XmlAttributeValue value = myAttribute.getValueElement();
        return value != null ? value.getTextOffset() + getModeRange().getStartOffset() : 0;
    }

    @Override
    public TextRange getTextRange() {
        final XmlAttributeValue value = myAttribute.getValueElement();
        return value != null ? TextRange.from(value.getTextOffset() + getModeRange().getStartOffset(), getModeRange().getLength()) : TextRange.from(0, 0);
    }

    @Override
    public ItemPresentation getPresentation() {
        return this;
    }

    @Nullable
    public Icon getIcon(boolean open) {
        return getIcon(0);
    }

    @Nullable
    public String getLocationString() {
        return null;
    }

    @Nullable
    public String getPresentableText() {
        final QName qName = getQName();
        return qName != null ? qName.toString() : hasPrefix() ? getPrefix() + ":" + getName() : getName();
    }

    @NotNull
    @Override
    @SuppressWarnings({ "RawUseOfParameterizedType" })
    public PsiElement getNavigationElement() {
        if (myNavigationElement == null && myAttribute.isValid()) {
            final XmlTag tag = myAttribute.getParent();
            final Class[] allInterfaces = CompletionLists.getAllInterfaces(tag.getClass());
            myNavigationElement = (PsiElement)Proxy.newProxyInstance(getClass().getClassLoader(), allInterfaces, new InvocationHandler() {
                @SuppressWarnings({"StringEquality", "AutoBoxing", "AutoUnboxing"})
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    try {
                        final ImplicitModeElement nameElement = ImplicitModeElement.this;

                        if (method.getName() == "navigate") {
                            nameElement.navigate((Boolean)args[0]);
                            //noinspection ConstantConditions
                            return null;
                        } else if (method.getName() == "canNavigate") {
                            return nameElement.canNavigate();
                        } else if (method.getName() == "getTextOffset") {
                            return nameElement.getTextOffset();
                        }
                        return method.invoke(tag, args);
                    } catch (InvocationTargetException e1) {
                        throw e1.getTargetException();
                    }
                }
            });
        }
        return myAttribute.isValid() ? myNavigationElement : this;
    }


    public boolean canNavigate() {
        return isValid() && myAttribute.getValueElement() != null;
    }

    @Override
    public void navigate(boolean b) {
        final Navigatable navigatable = ((Navigatable)myAttribute.getValueElement());
        if (navigatable != null) {
            navigatable.navigate(b);
        }
    }

    @Override
    public PsiElement getOriginalElement() {
        return myAttribute;
    }

    @Override
    @NotNull
    public SearchScope getUseScope() {
        return myAttribute.getUseScope();
    }

    @Override
    public PsiElement getParent() {
        return myAttribute.getParent();
    }

    @Override
    public PsiFile getContainingFile() {
        return myAttribute.getContainingFile();
    }

    public TextRange getModeRange() {
        final String value = myAttribute.getValue();
        final int p = value.indexOf(':');
        if (p == -1) {
            return TextRange.from(0, value.length());
        } else if (p == value.length() - 1) {
            return TextRange.from(0, 0);
        } else {
            return new TextRange(p + 1, value.length());
        }
    }

    public boolean hasPrefix() {
        return myAttribute.getValue().indexOf(':') != -1;
    }
}
