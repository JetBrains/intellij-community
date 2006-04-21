/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class DomManagerImpl extends DomManager implements ProjectComponent {
  static final Key<Module> MODULE = Key.create("NameStrategy");
  static final Key<Object> MOCK = Key.create("MockElement");
  private static final Key<DomNameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<DomInvocationHandler> CACHED_HANDLER = Key.create("CachedInvocationHandler");
  private static final Key<DomFileElementImpl> CACHED_FILE_ELEMENT = Key.create("CachedFileElement");

  private final List<DomEventListener> myListeners = new ArrayList<DomEventListener>();


  private final ConverterManagerImpl myConverterManager = new ConverterManagerImpl();
  private final Map<Type, GenericInfoImpl> myMethodsMaps = new HashMap<Type, GenericInfoImpl>();
  private final Map<Type, InvocationCache> myInvocationCaches = new HashMap<Type, InvocationCache>();
  private final Map<Class<? extends DomElement>, Class<? extends DomElement>> myImplementationClasses = new HashMap<Class<? extends DomElement>, Class<? extends DomElement>>();
  private final List<Function<DomElement, Collection<PsiElement>>> myPsiElementProviders = new ArrayList<Function<DomElement, Collection<PsiElement>>>();
  private final Set<Consumer<XmlFile>> myFileLoaders = new HashSet<Consumer<XmlFile>>();
  private final Map<XmlFile,Object> myNonDomFiles = new WeakHashMap<XmlFile, Object>();

  private DomEventListener[] myCachedListeners;
  private PomModelListener myXmlListener;
  private Project myProject;
  private PomModel myPomModel;
  private boolean myChanging;

  public DomManagerImpl(final PomModel pomModel, final Project project) {
    myPomModel = pomModel;
    myProject = project;
    final XmlAspect xmlAspect = myPomModel.getModelAspect(XmlAspect.class);
    assert xmlAspect != null;
    myXmlListener = new PomModelListener() {
      public synchronized void modelChanged(PomModelEvent event) {
        if (myChanging) return;
        final XmlChangeSet changeSet = (XmlChangeSet)event.getChangeSet(xmlAspect);
        if (changeSet != null) {
          new ExternalChangeProcessor(changeSet).processChanges();
        }
      }

      public boolean isAspectChangeInteresting(PomModelAspect aspect) {
        return xmlAspect.equals(aspect);
      }
    };
    myPomModel.addModelListener(myXmlListener);
    ReferenceProvidersRegistry.getInstance(myProject).registerReferenceProvider(XmlTag.class, new GenericValueReferenceProvider());
    ReferenceProvidersRegistry.getInstance(myProject).registerReferenceProvider(XmlAttributeValue.class, new GenericValueReferenceProvider());
  }

  public final void addDomEventListener(DomEventListener listener) {
    myCachedListeners = null;
    myListeners.add(listener);
  }

  public final void removeDomEventListener(DomEventListener listener) {
    myCachedListeners = null;
    myListeners.remove(listener);
  }

  public final ConverterManagerImpl getConverterManager() {
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

  public final GenericInfoImpl getGenericInfo(final Type type) {
    GenericInfoImpl genericInfoImpl = myMethodsMaps.get(type);
    if (genericInfoImpl == null) {
      if (type instanceof Class) {
        genericInfoImpl = new GenericInfoImpl((Class<? extends DomElement>)type, this);
        myMethodsMaps.put(type, genericInfoImpl);
      }
      else if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType)type;
        genericInfoImpl = new GenericInfoImpl((Class<? extends DomElement>)parameterizedType.getRawType(), this);
        myMethodsMaps.put(type, genericInfoImpl);
      }
      else {
        assert false : "Type not supported " + type;
      }
    }
    return genericInfoImpl;
  }

  final InvocationCache getInvocationCache(final Type type) {
    InvocationCache invocationCache = myInvocationCaches.get(type);
    if (invocationCache == null) {
      invocationCache = new InvocationCache();
      myInvocationCaches.put(type, invocationCache);
    }
    return invocationCache;
  }

  public static DomInvocationHandler getDomInvocationHandler(DomElement proxy) {
    final InvocationHandler handler = AdvancedProxy.getInvocationHandler(proxy);
    if (handler instanceof StableInvocationHandler) {
      return getDomInvocationHandler(((StableInvocationHandler)handler).getWrappedElement());
    }
    return (DomInvocationHandler)handler;
  }

  final DomElement createDomElement(final DomInvocationHandler handler) {
    synchronized (PsiLock.LOCK) {
      try {
        XmlTag tag = handler.getXmlTag();
        final Class abstractInterface = DomUtil.getRawType(handler.getDomElementType());
        final ClassChooser<? extends DomElement> classChooser = ClassChooserManager.getClassChooser(abstractInterface);
        final Class<? extends DomElement> concreteInterface = classChooser.chooseClass(tag);
        final DomElement element = doCreateDomElement(concreteInterface, handler);
        if (concreteInterface != abstractInterface) {
          handler.setType(concreteInterface);
        }
        handler.setProxy(element);
        handler.attach(tag);
        return element;
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new CodeGenerationException(e);
      }
    }
  }

  private DomElement doCreateDomElement(final Class<? extends DomElement> concreteInterface, final DomInvocationHandler handler) {
    final Class<? extends DomElement> aClass = getImplementation(concreteInterface);
    if (aClass != null) {
      return AdvancedProxy.createProxy(aClass, new Class[]{concreteInterface}, handler, Collections.<JavaMethodSignature>emptySet());
    }
    return AdvancedProxy.createProxy(handler, concreteInterface);
  }

  @Nullable
  Class<? extends DomElement> getImplementation(final Class<? extends DomElement> concreteInterface) {
    final Class<? extends DomElement> registeredImplementation = findImplementationClassDFS(concreteInterface);
    if (registeredImplementation != null) {
      return registeredImplementation;
    }
    final Implementation implementation = DomUtil.findAnnotationDFS(concreteInterface, Implementation.class);
    return implementation == null ? null : implementation.value();
  }

  private Class<? extends DomElement> findImplementationClassDFS(final Class<? extends DomElement> concreteInterface) {
    Class<? extends DomElement> aClass = myImplementationClasses.get(concreteInterface);
    if (aClass != null) {
      return aClass;
    }
    for (final Class aClass1 : concreteInterface.getInterfaces()) {
      aClass = findImplementationClassDFS(aClass1);
      if (aClass != null) {
        return aClass;
      }
    }
    return null;
  }

  public final Project getProject() {
    return myProject;
  }

  public static void setNameStrategy(final XmlFile file, final DomNameStrategy strategy) {
    file.putUserData(NAME_STRATEGY_KEY, strategy);
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
    return file != null ? file.getUserData(CACHED_FILE_ELEMENT) : null;
  }

  @Nullable
  public static DomInvocationHandler getCachedElement(final XmlElement xmlElement) {
    return xmlElement.getUserData(CACHED_HANDLER);
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
    myPomModel.removeModelListener(myXmlListener);
  }

  public final void projectOpened() {
  }

  public final void projectClosed() {
  }

  public <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass) {
    assert domElementClass.isAssignableFrom(implementationClass);
    myImplementationClasses.put(domElementClass, implementationClass);
  }

  public DomElement getDomElement(final XmlTag tag) {
    final DomInvocationHandler handler = _getDomElement(tag);
    return handler != null ? handler.getProxy() : null;
  }

  public final Collection<PsiElement> getPsiElements(final DomElement element) {
    return ContainerUtil.concat(myPsiElementProviders, new Function<Function<DomElement, Collection<PsiElement>>, Collection<PsiElement>>() {
      public Collection<PsiElement> fun(final Function<DomElement, Collection<PsiElement>> s) {
        return s.fun(element);
      }
    });
  }

  public void registerPsiElementProvider(Function<DomElement, Collection<PsiElement>> provider) {
    myPsiElementProviders.add(provider);
  }

  public void unregisterPsiElementProvider(Function<DomElement, Collection<PsiElement>> provider) {
    myPsiElementProviders.remove(provider);
  }

  @Nullable
  private DomInvocationHandler _getDomElement(final XmlTag tag) {
    if (tag == null) return null;

    DomInvocationHandler invocationHandler = getCachedElement(tag);
    if (invocationHandler != null && invocationHandler.isValid()) {
      return invocationHandler;
    }

    final XmlTag parentTag = tag.getParentTag();
    if (parentTag == null) {
      return getDomFileElement((XmlFile)tag.getContainingFile());
    }

    DomInvocationHandler parent = _getDomElement(parentTag);
    if (parent == null) return null;

    final GenericInfoImpl info = parent.getGenericInfo();
    final String tagName = tag.getName();
    final DomChildrenDescription childDescription = info.getChildDescription(tagName);
    if (childDescription == null) return null;

    childDescription.getValues(parent.getProxy());
    return getCachedElement(tag);
  }

  private DomInvocationHandler getDomFileElement(final XmlFile xmlFile) {
    if (xmlFile != null && !myNonDomFiles.containsKey(xmlFile)) {
      DomFileElementImpl element = getCachedElement(xmlFile);
      if (element == null) {
        final XmlFile originalFile = (XmlFile)xmlFile.getOriginalFile();
        final DomInvocationHandler originalElement = getDomFileElement(originalFile);
        if (originalElement != null) {
          final Class<? extends DomElement> aClass = (Class<? extends DomElement>)DomUtil.getRawType(originalElement.getDomElementType());
          final String rootTagName = originalElement.getXmlElementName();
          return getFileElement(xmlFile, aClass, rootTagName).getRootHandler();
        }

        for (final Consumer<XmlFile> fileLoader : myFileLoaders) {
          fileLoader.consume(xmlFile);
        }
        element = getCachedElement(xmlFile);
        if (element == null) {
          myNonDomFiles.put(xmlFile, new Object());
        }
      }

      if (element != null) {
        return element.getRootHandler();
      }
    }

    return null;
  }

  public final <T extends DomElement> T createMockElement(final Class<T> aClass, final Module module, final boolean physical) {
    final XmlFile file = (XmlFile)PsiManager.getInstance(myProject).getElementFactory().createFileFromText("a.xml", StdFileTypes.XML, "", 0, physical);
    final DomFileElementImpl<T> fileElement = getFileElement(file, aClass, "root");
    fileElement.putUserData(MODULE, module);
    fileElement.putUserData(MOCK, new Object());
    return fileElement.getRootElement();
  }

  public final boolean isMockElement(DomElement element) {
    final DomFileElement<?> root = element.getRoot();
    return root.getUserData(MOCK) != null;
  }

  public <T extends DomElement> T createStableValue(final Factory<T> provider) {
    final T initial = provider.create();
    final InvocationHandler handler = new StableInvocationHandler<T>(initial, provider);
    final Set<Class> intf = new HashSet<Class>();
    intf.addAll(Arrays.asList(initial.getClass().getInterfaces()));
    intf.add(StableElement.class);
    return AdvancedProxy.createProxy((Class<? extends T>)initial.getClass().getSuperclass(), intf.toArray(new Class[intf.size()]), handler, Collections.<JavaMethodSignature>emptySet());
  }

  public void registerFileLoader(Consumer<XmlFile> consumer) {
    myFileLoaders.add(consumer);
  }

  public void unregisterFileLoader(Consumer<XmlFile> consumer) {
    myFileLoaders.remove(consumer);
  }

  private static class StableInvocationHandler<T extends DomElement> implements InvocationHandler, StableElement {
    private T myCachedValue;
    private final Factory<T> myProvider;

    public StableInvocationHandler(final T initial, final Factory<T> provider) {
      myProvider = provider;
      myCachedValue = initial;
    }

    public final Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
      if (StableElement.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      }

      if (isNotValid(myCachedValue)) {
        if (AdvancedProxy.FINALIZE_METHOD.equals(method)) {
          return null;
        }

        myCachedValue = myProvider.create();
        if (isNotValid(myCachedValue)) {
          if (method != null && "isValid".equals(method.getName()) && DomElement.class.equals(method.getDeclaringClass())) {
            return Boolean.FALSE;
          }
          throw new AssertionError("Calling methods on invalid value");
        }
      }

      try {
        return method.invoke(myCachedValue, args);
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    public final void revalidate() {
      final T t = myProvider.create();
      if (!isNotValid(t) && !t.equals(myCachedValue)) {
        doInvalidate();
        myCachedValue = t;
      }
    }

    private void doInvalidate() {
      getDomInvocationHandler(myCachedValue).detach(true);
    }

    public final void invalidate() {
      if (!isNotValid(myCachedValue)) {
        doInvalidate();
      }
    }

    public final DomElement getWrappedElement() {
      if (isNotValid(myCachedValue)) {
        myCachedValue = myProvider.create();
      }
      return myCachedValue;
    }

    private boolean isNotValid(final T t) {
      return t == null || !t.isValid();
    }
  }
}