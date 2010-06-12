/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class DomFileElementImpl<T extends DomElement> implements DomFileElement<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomFileElementImpl");
  private static final DomGenericInfo EMPTY_DOM_GENERIC_INFO = new DomGenericInfo() {

    @Nullable
    public XmlElement getNameElement(DomElement element) {
      return null;
    }

    @Nullable
    public GenericDomValue getNameDomElement(DomElement element) {
      return null;
    }

    @Nullable
    public CustomDomChildrenDescription getCustomNameChildrenDescription() {
      return null;
    }

    @Nullable
    public String getElementName(DomElement element) {
      return null;
    }

    @NotNull
    public List<DomChildrenDescription> getChildrenDescriptions() {
      return Collections.emptyList();
    }

    @NotNull
    public List<DomFixedChildDescription> getFixedChildrenDescriptions() {
      return Collections.emptyList();
    }

    @NotNull
    public List<DomCollectionChildDescription> getCollectionChildrenDescriptions() {
      return Collections.emptyList();
    }

    @NotNull
    public List<DomAttributeChildDescription> getAttributeChildrenDescriptions() {
      return Collections.emptyList();
    }

    public boolean isTagValueElement() {
      return false;
    }

    @Nullable
    public DomFixedChildDescription getFixedChildDescription(String tagName) {
      return null;
    }

    @Nullable
    public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    @Nullable
    public DomCollectionChildDescription getCollectionChildDescription(String tagName) {
      return null;
    }

    @Nullable
    public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    public DomAttributeChildDescription getAttributeChildDescription(String attributeName) {
      return null;
    }

    @Nullable
    public DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
      return null;
    }

  };

  private final XmlFile myFile;
  private final DomFileDescription<T> myFileDescription;
  private final DomRootInvocationHandler myRootHandler;
  private final Class<T> myRootElementClass;
  private final EvaluatedXmlNameImpl myRootTagName;
  private final DomManagerImpl myManager;
  private final Map<Key,Object> myUserData = new HashMap<Key, Object>();

  protected DomFileElementImpl(final XmlFile file,
                               final Class<T> rootElementClass,
                               final EvaluatedXmlNameImpl rootTagName,
                               final DomManagerImpl manager, final DomFileDescription<T> fileDescription) {
    myFile = file;
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
    myManager = manager;
    myFileDescription = fileDescription;
    myRootHandler = new DomRootInvocationHandler(rootElementClass, new RootDomParentStrategy(this), this, rootTagName);
  }

  @NotNull
  public final XmlFile getFile() {
    return myFile;
  }

  @NotNull
  public XmlFile getOriginalFile() {
    return (XmlFile)myFile.getOriginalFile();
  }

  @Nullable
  public XmlTag getRootTag() {
    if (!myFile.isValid()) {
      return null;
    }

    final XmlDocument document = myFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null) {
        if (tag.getTextLength() > 0 && getFileDescription().acceptsOtherRootTagNames()) return tag;
        if (myRootTagName.getXmlName().getLocalName().equals(tag.getLocalName()) &&
            myRootTagName.isNamespaceAllowed(this, tag.getNamespace())) {
          return tag;
        }
      }
    }
    return null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof DomFileElementImpl)) return false;

    final DomFileElementImpl that = (DomFileElementImpl)o;

    if (myFile != null ? !myFile.equals(that.myFile) : that.myFile != null) return false;
    if (myRootElementClass != null ? !myRootElementClass.equals(that.myRootElementClass) : that.myRootElementClass != null) return false;
    if (myRootTagName != null ? !myRootTagName.equals(that.myRootTagName) : that.myRootTagName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myFile != null ? myFile.hashCode() : 0);
    result = 31 * result + (myRootElementClass != null ? myRootElementClass.hashCode() : 0);
    result = 31 * result + (myRootTagName != null ? myRootTagName.hashCode() : 0);
    return result;
  }

  @NotNull
  public final DomManagerImpl getManager() {
    return myManager;
  }

  public final Type getDomElementType() {
    return getClass();
  }

  @NotNull
  public AbstractDomChildrenDescription getChildDescription() {
    throw new UnsupportedOperationException("Method getChildDescription is not yet implemented in " + getClass().getName());
  }

  public DomNameStrategy getNameStrategy() {
    return getRootHandler().getNameStrategy();
  }

  @NotNull
  public ElementPresentation getPresentation() {
    return new DomElementPresentation() {

      public @NonNls String getElementName() {
        return "<ROOT>";
      }

      public @NonNls String getTypeName() {
        return "<ROOT>";
      }

      public Icon getIcon() {
        return null;
      }
    };
  }

  public GlobalSearchScope getResolveScope() {
    return myFile.getResolveScope();
  }

  @Nullable
  public <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    return DomFileElement.class.isAssignableFrom(requiredClass) && !strict ? (T)this : null;
  }

  public Module getModule() {
    return ModuleUtil.findModuleForPsiElement(getFile());
  }

  public void copyFrom(DomElement other) {
    throw new UnsupportedOperationException("Method copyFrom is not yet implemented in " + getClass().getName());
  }

  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    throw new UnsupportedOperationException("Method createMockCopy is not yet implemented in " + getClass().getName());
  }

  public final <T extends DomElement> T createStableCopy() {
    return myManager.createStableValue(new Factory<T>() {
      @Nullable
      public T create() {
        return (T)myManager.getFileElement(myFile);
      }
    });
  }

  @NotNull
  public String getXmlElementNamespace() {
    return "";
  }

  @Nullable
  @NonNls
  public String getXmlElementNamespaceKey() {
    return null;
  }

  @NotNull
  public final T getRootElement() {
    if (!isValid()) {
      if (!myFile.isValid()) {
        assert false: myFile + " is not valid";
      } else {
        final DomFileElementImpl<DomElement> fileElement = myManager.getFileElement(myFile);
        if (fileElement == null) {
          final FileDescriptionCachedValueProvider<DomElement> provider = myManager.getOrCreateCachedValueProvider(myFile);
          String s = provider.getFileElementWithLogging();
          LOG.error("Null, log=" + s);
        } else {
          assert false: this + " does not equal to " + fileElement;
        }
      }
    }
    return (T)getRootHandler().getProxy();
  }

  @NotNull
  public Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  @NotNull
  public DomFileDescription<T> getFileDescription() {
    return myFileDescription;
  }

  @NotNull
  protected final DomRootInvocationHandler getRootHandler() {
    return myRootHandler;
  }

  public @NonNls String toString() {
    return "File " + myFile.toString();
  }

  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return null;
  }

  public final XmlTag getXmlTag() {
    return null;
  }

  @NotNull
  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    return (DomFileElementImpl<T>)this;
  }

  @Nullable
  public DomElement getParent() {
    return null;
  }

  public final XmlTag ensureTagExists() {
    return null;
  }

  public final XmlElement getXmlElement() {
    return getFile();
  }

  public final XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  public void undefine() {
  }

  public final boolean isValid() {
    return myFile.isValid() && equals(myManager.getFileElement(myFile));
  }

  @NotNull
  public final DomGenericInfo getGenericInfo() {
    return EMPTY_DOM_GENERIC_INFO;
  }

  @NotNull
  public String getXmlElementName() {
    return "";
  }

  public void accept(final DomElementVisitor visitor) {
    myManager.getApplicationComponent().getVisitorDescription(visitor.getClass()).acceptElement(visitor, this);
  }

  public void acceptChildren(DomElementVisitor visitor) {
    getRootElement().accept(visitor);
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myUserData.put(key, value);
  }

  public final long getModificationCount() {
    return myFile.getModificationStamp();
  }

}
