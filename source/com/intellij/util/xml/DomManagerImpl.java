/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import net.sf.cglib.proxy.Proxy;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.core.CodeGenerationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * @author peter
 */
public class DomManagerImpl extends DomManager implements ApplicationComponent {
  private static final Key<NameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<DomInvocationHandler> CACHED_HANDLER = Key.create("CachedInvocationHandler");
  private static final Key<DomFileElement> CACHED_FILE_ELEMENT = Key.create("CachedFileElement");

  private final List<DomEventListener> myListeners = new ArrayList<DomEventListener>();
  private final ConverterManager myConverterManager = new ConverterManager();
  private final Map<Class<? extends DomElement>,Class> myClass2ProxyClass = new HashMap<Class<? extends DomElement>, Class>();
  private final Map<Class<? extends DomElement>,MethodsMap> myMethodsMaps = new HashMap<Class<? extends DomElement>, MethodsMap>();
  private final Map<Class<? extends DomElement>,ClassChooser> myClassChoosers = new HashMap<Class<? extends DomElement>, ClassChooser>();
  private DomEventListener[] myCachedListeners;

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
  public final <T extends DomElement> DomFileElement<T> getFileElement(final XmlFile file,
                                                                 final Class<T> aClass,
                                                                 String rootTagName) {
    synchronized (PsiLock.LOCK) {
      DomFileElement<T> element = getCachedElement(file);
      if (element == null) {
        element = new DomFileElement<T>(file, aClass, rootTagName, this);
        setCachedElement(file, element);
      }
      return element;
    }
  }

  protected static void setCachedElement(final XmlFile file, final DomFileElement element) {
    file.putUserData(CACHED_FILE_ELEMENT, element);
  }

  protected static void setCachedElement(final XmlTag tag, final DomInvocationHandler element) {
    if (tag != null) {
      tag.putUserData(CACHED_HANDLER, element);
    }
  }

  @Nullable
  public static DomFileElement getCachedElement(final XmlFile file) {
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

  public final void initComponent() {
  }

  public final void disposeComponent() {
  }

  public <T extends DomElement> void registerClassChooser(final Class<T> aClass, final ClassChooser<T> classChooser) {
    myClassChoosers.put(aClass, classChooser);
  }

  public <T extends DomElement> void unregisterClassChooser(Class<T> aClass) {
    myClassChoosers.remove(aClass);
  }

}
