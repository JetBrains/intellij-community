/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.Key;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.*;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.AttributeValueChangeEvent;
import com.intellij.util.xml.events.ContentsChangedEvent;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.TagValueChangeEvent;
import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class DomManagerImpl extends DomManager implements ProjectComponent, XmlChangeVisitor {
  private static final Key<NameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<DomInvocationHandler> CACHED_HANDLER = Key.create("CachedInvocationHandler");
  private static final Key<DomFileElementImpl> CACHED_FILE_ELEMENT = Key.create("CachedFileElement");

  private final List<DomEventListener> myListeners = new ArrayList<DomEventListener>();
  private final ConverterManager myConverterManager = new ConverterManager();
  private final Map<Class<? extends DomElement>,Class> myClass2ProxyClass = new HashMap<Class<? extends DomElement>, Class>();
  private final Map<Class<? extends DomElement>,MethodsMap> myMethodsMaps = new HashMap<Class<? extends DomElement>, MethodsMap>();
  private final Map<Class<? extends DomElement>,ClassChooser> myClassChoosers = new HashMap<Class<? extends DomElement>, ClassChooser>();
  private DomEventListener[] myCachedListeners;
  private PomModelListener myXmlListener;
  private PomModel myPomModel;
  private boolean myChanging;


  public DomManagerImpl(final PomModel pomModel) {
    myPomModel = pomModel;
  }

  public final void addDomEventListener(DomEventListener listener) {
    myCachedListeners = null;
    myListeners.add(listener);
  }

  public final void removeDomEventListener(DomEventListener listener) {
    myCachedListeners = null;
    myListeners.remove(listener);
  }

  public final ConverterManager getConverterManager() {
    return myConverterManager;
  }

  protected final void fireEvent(DomEvent event) {
    DomEventListener[] listeners = myCachedListeners;
    if (listeners == null) {
      listeners = myCachedListeners = myListeners.toArray(new DomEventListener[myListeners.size()]);
    }
    for (DomEventListener listener : listeners) {
      listener.eventOccured(event);
    }
  }

  final <T extends DomElement>MethodsMap getMethodsMap(final Class<T> aClass) {
    MethodsMap methodsMap = myMethodsMaps.get(aClass);
    if (methodsMap == null) {
      methodsMap = new MethodsMap(aClass);
      myMethodsMaps.put(aClass, methodsMap);
    }
    return methodsMap;
  }

  private Class getConcreteType(Class aClass, XmlTag tag) {
    final ClassChooser classChooser = myClassChoosers.get(aClass);
    return classChooser == null ? aClass : classChooser.chooseClass(tag);
  }

  final DomElement createDomElement(final Class aClass,
                                                     final XmlTag tag,
                                                     final DomInvocationHandler handler) {
    synchronized (PsiLock.LOCK) {
      try {
        Class clazz = getProxyClassFor(getConcreteType(aClass, tag));
        final DomElement element = (DomElement) clazz.getConstructor(InvocationHandler.class).newInstance(handler);
        handler.setProxy(element);
        setCachedElement(tag, handler);
        return element;
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new CodeGenerationException(e);
      }
    }
  }

  private Class getProxyClassFor(final Class<? extends DomElement> aClass) {
    Class proxyClass = myClass2ProxyClass.get(aClass);
    if (proxyClass == null) {
      proxyClass = Proxy.getProxyClass(null, new Class[]{aClass});
      myClass2ProxyClass.put(aClass, proxyClass);
    }
    return proxyClass;
  }

  public final void setNameStrategy(final XmlFile file, final NameStrategy strategy) {
    file.putUserData(NAME_STRATEGY_KEY, strategy);
  }

  @NotNull
  public final NameStrategy getNameStrategy(final XmlFile file) {
    return _getNameStrategy(file);
  }

  protected static NameStrategy _getNameStrategy(final XmlFile file) {
    final NameStrategy strategy = file.getUserData(NAME_STRATEGY_KEY);
    return strategy == null ? NameStrategy.HYPHEN_STRATEGY : strategy;
  }

  @NotNull
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(final XmlFile file,
                                                                 final Class<T> aClass,
                                                                 String rootTagName) {
    synchronized (PsiLock.LOCK) {
      DomFileElementImpl<T> element = getCachedElement(file);
      if (element == null) {
        element = new DomFileElementImpl<T>(file, aClass, rootTagName, this);
        setCachedElement(file, element);
      }
      return element;
    }
  }

  protected static void setCachedElement(final XmlFile file, final DomFileElementImpl element) {
    file.putUserData(CACHED_FILE_ELEMENT, element);
  }

  protected static void setCachedElement(final XmlTag tag, final DomInvocationHandler element) {
    if (tag != null) {
      tag.putUserData(CACHED_HANDLER, element);
    }
  }

  @Nullable
  public static DomFileElementImpl getCachedElement(final XmlFile file) {
    return file.getUserData(CACHED_FILE_ELEMENT);
  }

  @Nullable
  public static DomInvocationHandler getCachedElement(final XmlTag tag) {
    return tag.getUserData(CACHED_HANDLER);
  }

  @NonNls
  public final String getComponentName() {
    return getClass().getName();
  }

  public final synchronized boolean setChanging(final boolean changing) {
    boolean oldChanging = myChanging;
    myChanging = changing;
    return oldChanging;
  }

  public final boolean isChanging() {
    return myChanging;
  }

  public final void initComponent() {
  }

  public final void disposeComponent() {
  }

  public final <T extends DomElement> void registerClassChooser(final Class<T> aClass, final ClassChooser<T> classChooser) {
    myClassChoosers.put(aClass, classChooser);
  }

  public final <T extends DomElement> void unregisterClassChooser(Class<T> aClass) {
    myClassChoosers.remove(aClass);
  }

  public final void projectOpened() {
    final XmlAspect xmlAspect = myPomModel.getModelAspect(XmlAspect.class);
    assert xmlAspect != null;
    myXmlListener = new PomModelListener() {
      public void modelChanged(PomModelEvent event) {
        if (myChanging) return;
        final XmlChangeSet changeSet = (XmlChangeSet) event.getChangeSet(xmlAspect);
        if (changeSet != null) {
          for (XmlChange change : changeSet.getChanges()) {
            change.accept(DomManagerImpl.this);
          }
        }
      }

      public boolean isAspectChangeInteresting(PomModelAspect aspect) {
        return xmlAspect.equals(aspect);
      }
    };
    myPomModel.addModelListener(myXmlListener);
  }

  public final void projectClosed() {
    myPomModel.removeModelListener(myXmlListener);
  }

  public final void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet) {
    final DomInvocationHandler element = getCachedElement(xmlAttributeSet.getTag());
    if (element != null) {
      fireEvent(new AttributeValueChangeEvent(element, xmlAttributeSet.getName(), xmlAttributeSet.getValue()));
    }
  }

  static void invalidateSubtree(final XmlTag root, final boolean invalidateRoot) {
    final DomInvocationHandler element = getCachedElement(root);
    if (element != null) {
      setCachedElement(root, null);
      if (invalidateRoot) {
        element.invalidate();
      }
      for (XmlTag tag : root.getSubTags()) {
        invalidateSubtree(tag, true);
      }
    }
  }

  public final void visitDocumentChanged(final XmlDocumentChanged change) {
    final DomFileElementImpl element = getCachedElement((XmlFile)change.getDocument().getContainingFile());
    if (element != null) {
      final XmlTag rootTag = element.getRootTag();
      if (rootTag != null) {
        invalidateSubtree(rootTag, true);
      }
      element.invalidateRoot();
      fireEvent(new ContentsChangedEvent(element));
    }
  }

  public final void visitXmlElementChanged(final XmlElementChanged change) {
    xmlElementChanged(change.getElement());
  }

  private void xmlElementChanged(final XmlElement xmlElement) {
    if (isTagValueChange(xmlElement)) {
      fireTagValueChanged(((XmlText)xmlElement).getParentTag());
    }
  }

  private final boolean isTagValueChange(final XmlElement xmlElement) {
    final PsiElement parent = xmlElement.getParent();
    if (parent instanceof XmlTag) {
      return isTagValueChange(xmlElement, (XmlTag) parent);
    }
    return false;
  }

  private final boolean isTagValueChange(final XmlElement xmlElement, XmlTag parent) {
    if (xmlElement instanceof XmlText) {
      return ((XmlTag)parent).getSubTags().length == 0;
    }
    return false;
  }

  public final void visitXmlTagChildAdd(final XmlTagChildAdd change) {
    final XmlTagChild child = change.getChild();
    final XmlTag tag = change.getTag();
    if (isTagValueChange(child)) {
      fireTagValueChanged(tag);
    }
    if (child instanceof XmlTag) {
      XmlTag childTag = (XmlTag)child;
      final DomInvocationHandler element = getCachedElement(tag);
      if (element != null) {
        element.processChildTagAdded(childTag);
      }
    }
  }

  private void fireTagValueChanged(final XmlTag tag) {
    final DomInvocationHandler element = getCachedElement(tag);
    if (element != null) {
      fireEvent(new TagValueChangeEvent(element, DomInvocationHandler.getTagValue(tag)));
    }
  }

  public final void visitXmlTagChildChanged(final XmlTagChildChanged change) {
    xmlElementChanged(change.getChild());
  }

  public final void visitXmlTagChildRemoved(final XmlTagChildRemoved change) {
    final XmlTag tag = change.getTag();
    if (isTagValueChange(change.getChild(), tag)) {
      fireTagValueChanged(tag);
    }
  }

  public final void visitXmlTagNameChanged(final XmlTagNameChanged xmlTagNameChanged) {
    xmlElementChanged(xmlTagNameChanged.getTag());
  }

  public final void visitXmlTextChanged(final XmlTextChanged xmlTextChanged) {
    fireTagValueChanged(xmlTextChanged.getText().getParentTag());
  }

}