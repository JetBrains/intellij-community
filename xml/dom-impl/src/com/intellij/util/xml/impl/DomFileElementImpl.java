// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.semantic.SemElement;
import com.intellij.util.ObjectUtils;
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
public class DomFileElementImpl<T extends DomElement> implements DomFileElement<T>, SemElement {
  private static final DomGenericInfo EMPTY_DOM_GENERIC_INFO = new DomGenericInfo() {
    @Override
    public @Nullable GenericDomValue getNameDomElement(DomElement element) {
      return null;
    }

    @Override
    public @NotNull List<? extends CustomDomChildrenDescription> getCustomNameChildrenDescription() {
      return Collections.emptyList();
    }

    @Override
    public @Nullable String getElementName(DomElement element) {
      return null;
    }

    @Override
    public @NotNull List<DomChildrenDescription> getChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<DomFixedChildDescription> getFixedChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<DomCollectionChildDescription> getCollectionChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<DomAttributeChildDescription<?>> getAttributeChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Override
    public boolean isTagValueElement() {
      return false;
    }

    @Override
    public @Nullable DomFixedChildDescription getFixedChildDescription(String tagName) {
      return null;
    }

    @Override
    public @Nullable DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    @Override
    public @Nullable DomCollectionChildDescription getCollectionChildDescription(String tagName) {
      return null;
    }

    @Override
    public @Nullable DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    @Override
    public DomAttributeChildDescription getAttributeChildDescription(String attributeName) {
      return null;
    }

    @Override
    public @Nullable DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
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

  protected DomFileElementImpl(XmlFile file, EvaluatedXmlNameImpl rootTagName, DomFileDescription<T> fileDescription, FileStub stub) {
    myFile = file;
    myRootElementClass = fileDescription.getRootElementClass();
    myRootTagName = rootTagName;
    myManager = DomManagerImpl.getDomManager(file.getProject());
    myFileDescription = fileDescription;
    myRootHandler = new DomRootInvocationHandler(myRootElementClass, new RootDomParentStrategy(this), this, rootTagName,
                                                 stub == null ? null : stub.getRootTagStub());
  }

  @Override
  public final @NotNull XmlFile getFile() {
    return myFile;
  }

  @Override
  public @NotNull XmlFile getOriginalFile() {
    return (XmlFile)myFile.getOriginalFile();
  }

  @Override
  public @Nullable XmlTag getRootTag() {
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
  public final @NotNull DomManagerImpl getManager() {
    return myManager;
  }

  @Override
  public final Type getDomElementType() {
    return getClass();
  }

  @Override
  public @NotNull AbstractDomChildrenDescription getChildDescription() {
    throw new UnsupportedOperationException("Method getChildDescription is not yet implemented in " + getClass().getName());
  }

  @Override
  public DomNameStrategy getNameStrategy() {
    return getRootHandler().getNameStrategy();
  }

  @Override
  public @NotNull ElementPresentation getPresentation() {
    return new ElementPresentation() {

      @Override
      @NonNls
      public String getElementName() {
        return "<ROOT>";
      }

      @Override
      @NonNls
      public String getTypeName() {
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
  public @Nullable <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
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
    PsiManager psiManager = myFile.getManager();
    VirtualFile vFile = myFile.getViewProvider().getVirtualFile();
    //noinspection unchecked
    return myManager.createStableValue(() -> (T)myManager.getFileElement(ObjectUtils.tryCast(psiManager.findFile(vFile), XmlFile.class)));
  }

  @Override
  public @NotNull String getXmlElementNamespace() {
    return "";
  }

  @Override
  @NonNls
  public @Nullable String getXmlElementNamespaceKey() {
    return null;
  }

  @Override
  public final @NotNull T getRootElement() {
    if (!isValid()) {
      PsiUtilCore.ensureValid(myFile);
      throw new AssertionError(this + " is not equal to " + myManager.getFileElement(myFile));
    }
    return (T)getRootHandler().getProxy();
  }

  @Override
  public @NotNull Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  @Override
  public @NotNull DomFileDescription<T> getFileDescription() {
    return myFileDescription;
  }

  protected final @NotNull DomRootInvocationHandler getRootHandler() {
    return myRootHandler;
  }

  @NonNls
  public String toString() {
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

  public @NotNull <T extends DomElement> DomFileElementImpl<T> getRoot() {
    return (DomFileElementImpl<T>)this;
  }

  @Override
  public @Nullable DomElement getParent() {
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

  public @Nullable String checkValidity() {
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
  public final @NotNull DomGenericInfo getGenericInfo() {
    return EMPTY_DOM_GENERIC_INFO;
  }

  @Override
  public @NotNull String getXmlElementName() {
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
