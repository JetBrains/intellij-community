/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import com.intellij.util.xml.stubs.FileStub;
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

    @Override
    @Nullable
    public XmlElement getNameElement(DomElement element) {
      return null;
    }

    @Override
    @Nullable
    public GenericDomValue getNameDomElement(DomElement element) {
      return null;
    }

    @Override
    @NotNull
    public List<? extends CustomDomChildrenDescription> getCustomNameChildrenDescription() {
      return Collections.emptyList();
    }

    @Override
    @Nullable
    public String getElementName(DomElement element) {
      return null;
    }

    @Override
    @NotNull
    public List<DomChildrenDescription> getChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public List<DomFixedChildDescription> getFixedChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public List<DomCollectionChildDescription> getCollectionChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public List<DomAttributeChildDescription> getAttributeChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    public boolean isTagValueElement() {
      return false;
    }

    @Override
    @Nullable
    public DomFixedChildDescription getFixedChildDescription(String tagName) {
      return null;
    }

    @Override
    @Nullable
    public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    @Override
    @Nullable
    public DomCollectionChildDescription getCollectionChildDescription(String tagName) {
      return null;
    }

    @Override
    @Nullable
    public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    @Override
    public DomAttributeChildDescription getAttributeChildDescription(String attributeName) {
      return null;
    }

    @Override
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
  private final Map<Key,Object> myUserData = new HashMap<>();

  protected DomFileElementImpl(final XmlFile file,
                               final Class<T> rootElementClass,
                               final EvaluatedXmlNameImpl rootTagName,
                               final DomManagerImpl manager, final DomFileDescription<T> fileDescription,
                               FileStub stub) {
    myFile = file;
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
    myManager = manager;
    myFileDescription = fileDescription;
    myRootHandler = new DomRootInvocationHandler(rootElementClass, new RootDomParentStrategy(this), this, rootTagName,
                                                 stub == null ? null : stub.getRootTagStub());
  }

  @Override
  @NotNull
  public final XmlFile getFile() {
    return myFile;
  }

  @Override
  @NotNull
  public XmlFile getOriginalFile() {
    return (XmlFile)myFile.getOriginalFile();
  }

  @Override
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

  @Override
  @NotNull
  public final DomManagerImpl getManager() {
    return myManager;
  }

  @Override
  public final Type getDomElementType() {
    return getClass();
  }

  @Override
  @NotNull
  public AbstractDomChildrenDescription getChildDescription() {
    throw new UnsupportedOperationException("Method getChildDescription is not yet implemented in " + getClass().getName());
  }

  @Override
  public DomNameStrategy getNameStrategy() {
    return getRootHandler().getNameStrategy();
  }

  @Override
  @NotNull
  public ElementPresentation getPresentation() {
    return new ElementPresentation() {

      @Override
      public @NonNls String getElementName() {
        return "<ROOT>";
      }

      @Override
      public @NonNls String getTypeName() {
        return "<ROOT>";
      }

      @Override
      public Icon getIcon() {
        return null;
      }
    };
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myFile.getResolveScope();
  }

  @Override
  @Nullable
  public <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    return DomFileElement.class.isAssignableFrom(requiredClass) && !strict ? (T)this : null;
  }

  @Override
  public Module getModule() {
    return ModuleUtilCore.findModuleForPsiElement(getFile());
  }

  @Override
  public void copyFrom(DomElement other) {
    throw new UnsupportedOperationException("Method copyFrom is not yet implemented in " + getClass().getName());
  }

  @Override
  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    throw new UnsupportedOperationException("Method createMockCopy is not yet implemented in " + getClass().getName());
  }

  @Override
  public final <T extends DomElement> T createStableCopy() {
    return myManager.createStableValue(() -> (T)myManager.getFileElement(myFile));
  }

  @Override
  @NotNull
  public String getXmlElementNamespace() {
    return "";
  }

  @Override
  @Nullable
  @NonNls
  public String getXmlElementNamespaceKey() {
    return null;
  }

  @Override
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

  @Override
  @NotNull
  public Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  @Override
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

  @Override
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return null;
  }

  @Override
  public final XmlTag getXmlTag() {
    return null;
  }

  @NotNull
  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    return (DomFileElementImpl<T>)this;
  }

  @Override
  @Nullable
  public DomElement getParent() {
    return null;
  }

  @Override
  public final XmlTag ensureTagExists() {
    return null;
  }

  @Override
  public final XmlElement getXmlElement() {
    return getFile();
  }

  @Override
  public final XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  @Override
  public void undefine() {
  }

  @Override
  public final boolean isValid() {
    return checkValidity() == null;
  }

  @Override
  public boolean exists() {
    return true;
  }

  @Nullable
  public String checkValidity() {
    if (!myFile.isValid()) {
      return "Invalid file";
    }
    final DomFileElementImpl<DomElement> fileElement = myManager.getFileElement(myFile);
    if (!equals(fileElement)) {
      return "file element changed: " + fileElement + "; fileType=" + myFile.getFileType();
    }
    return null;
  }

  @Override
  @NotNull
  public final DomGenericInfo getGenericInfo() {
    return EMPTY_DOM_GENERIC_INFO;
  }

  @Override
  @NotNull
  public String getXmlElementName() {
    return "";
  }

  @Override
  public void accept(final DomElementVisitor visitor) {
    myManager.getApplicationComponent().getVisitorDescription(visitor.getClass()).acceptElement(visitor, this);
  }

  @Override
  public void acceptChildren(DomElementVisitor visitor) {
    getRootElement().accept(visitor);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return (T)myUserData.get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myUserData.put(key, value);
  }

  @Override
  public final long getModificationCount() {
    return myFile.getModificationStamp();
  }

}
