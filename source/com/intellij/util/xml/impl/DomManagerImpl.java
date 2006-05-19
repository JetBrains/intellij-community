/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.InstanceMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final Key<DomFileDescription> CACHED_FILE_DESCRIPTION = Key.create("CachedFileDescription");

  private static final InstanceMap<ScopeProvider> ourScopeProviders = new InstanceMap<ScopeProvider>();
  private static final Map<Type, GenericInfoImpl> ourMethodsMaps = new HashMap<Type, GenericInfoImpl>();

  private final EventDispatcher<DomEventListener> myListeners = EventDispatcher.create(DomEventListener.class);


  private final ConverterManagerImpl myConverterManager = new ConverterManagerImpl();
  private final Map<Pair<Type, Type>, InvocationCache> myInvocationCaches = new HashMap<Pair<Type, Type>, InvocationCache>();
  private final Map<Class<? extends DomElement>, Class<? extends DomElement>> myImplementationClasses =
    new HashMap<Class<? extends DomElement>, Class<? extends DomElement>>();
  private final Map<Class<? extends DomElement>, Class<? extends DomElement>> myCachedImplementationClasses =
    new HashMap<Class<? extends DomElement>, Class<? extends DomElement>>();
  private final List<Function<DomElement, Collection<PsiElement>>> myPsiElementProviders =
    new ArrayList<Function<DomElement, Collection<PsiElement>>>();
  private final Set<DomFileDescription> myFileDescriptions = new HashSet<DomFileDescription>();
  private final Map<XmlFile, Object> myNonDomFiles = new WeakHashMap<XmlFile, Object>();

  private static final InvocationStack ourInvocationStack = new InvocationStack();

  private Project myProject;
  private boolean myChanging;

  private final GenericValueReferenceProvider myGenericValueReferenceProvider = new GenericValueReferenceProvider();
  private final ReferenceProvidersRegistry myReferenceProvidersRegistry;
  private final PsiElementFactory myElementFactory;

  public DomManagerImpl(final PomModel pomModel,
                        final Project project,
                        final ReferenceProvidersRegistry registry,
                        final PsiManager psiManager,
                        final XmlAspect xmlAspect) {
    myProject = project;
    pomModel.addModelListener(new PomModelListener() {
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
    });
    myReferenceProvidersRegistry = registry;
    myElementFactory = psiManager.getElementFactory();
  }

  public static DomManagerImpl getDomManager(Project project) {
    return (DomManagerImpl)project.getComponent(DomManager.class);
  }

  public static InvocationStack getInvocationStack() {
    return ourInvocationStack;
  }

  public final void addDomEventListener(DomEventListener listener) {
    myListeners.addListener(listener);
  }

  public void addDomEventListener(DomEventListener listener, Disposable parentDisposable) {
    myListeners.addListener(listener, parentDisposable);
  }

  public final void removeDomEventListener(DomEventAdapter listener) {
    myListeners.removeListener(listener);
  }

  public final ConverterManager getConverterManager() {
    return myConverterManager;
  }

  public void addPsiReferenceFactoryForClass(Class clazz, PsiReferenceFactory psiReferenceFactory) {
    myGenericValueReferenceProvider.addReferenceProviderForClass(clazz, psiReferenceFactory);
  }

  public ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  protected final void fireEvent(DomEvent event) {
    myListeners.getMulticaster().eventOccured(event);
  }

  public static ScopeProvider getScopeProvider(Class<? extends ScopeProvider> aClass) {
    return ourScopeProviders.get(aClass);
  }

  public final GenericInfoImpl getGenericInfo(final Type type) {
    GenericInfoImpl genericInfoImpl = ourMethodsMaps.get(type);
    if (genericInfoImpl == null) {
      if (type instanceof Class) {
        genericInfoImpl = new GenericInfoImpl((Class<? extends DomElement>)type, this);
        ourMethodsMaps.put(type, genericInfoImpl);
      }
      else if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType)type;
        genericInfoImpl = new GenericInfoImpl((Class<? extends DomElement>)parameterizedType.getRawType(), this);
        ourMethodsMaps.put(type, genericInfoImpl);
      }
      else {
        assert false : "Type not supported " + type;
      }
    }
    return genericInfoImpl;
  }

  final InvocationCache getInvocationCache(final Pair<Type, Type> type) {
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

  public static StableInvocationHandler getStableInvocationHandler(Object proxy) {
    return (StableInvocationHandler)AdvancedProxy.getInvocationHandler(proxy);
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
    if (myCachedImplementationClasses.containsKey(concreteInterface)) {
      return myCachedImplementationClasses.get(concreteInterface);
    }

    final TreeSet<Class<? extends DomElement>> set =
      new TreeSet<Class<? extends DomElement>>(new Comparator<Class<? extends DomElement>>() {
        public int compare(final Class<? extends DomElement> o1, final Class<? extends DomElement> o2) {
          if (o1.isAssignableFrom(o2)) return 1;
          if (o2.isAssignableFrom(o1)) return -1;
          if (o1.equals(o2)) return 0;
          throw new AssertionError("Incompatible implementation classes: " + o1 + " & " + o2);
        }
      });
    findImplementationClassDFS(concreteInterface, set);
    if (!set.isEmpty()) {
      final Class<? extends DomElement> aClass = set.last();
      myCachedImplementationClasses.put(concreteInterface, aClass);
      return aClass;
    }
    final Implementation implementation = DomUtil.findAnnotationDFS(concreteInterface, Implementation.class);
    final Class<? extends DomElement> aClass1 = implementation == null ? null : implementation.value();
    myCachedImplementationClasses.put(concreteInterface, aClass1);
    return aClass1;
  }

  private void findImplementationClassDFS(final Class<? extends DomElement> concreteInterface, SortedSet<Class<? extends DomElement>> results) {
    Class<? extends DomElement> aClass = myImplementationClasses.get(concreteInterface);
    if (aClass != null) {
      results.add(aClass);
    }
    else {
      for (final Class aClass1 : concreteInterface.getInterfaces()) {
        findImplementationClassDFS(aClass1, results);
      }
    }
  }

  public final Project getProject() {
    return myProject;
  }

  public static void setNameStrategy(final XmlFile file, final DomNameStrategy strategy) {
    file.putUserData(NAME_STRATEGY_KEY, strategy);
  }

  @NotNull
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(final XmlFile file, final Class<T> aClass, String rootTagName) {
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

  @NotNull
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
    return ContainerUtil
      .concat(myPsiElementProviders, new Function<Function<DomElement, Collection<PsiElement>>, Collection<PsiElement>>() {
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

  public final DomRootInvocationHandler getDomFileElement(final XmlFile xmlFile) {
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

        for (final DomFileDescription description : myFileDescriptions) {
          if (description.isMyFile(xmlFile)) {
            return getFileElement(xmlFile, description.getRootElementClass(), description.getRootTagName()).getRootHandler();
          }
        }

        myNonDomFiles.put(xmlFile, new Object());
      }

      if (element != null) {
        return element.getRootHandler();
      }
    }

    return null;
  }

  public final <T extends DomElement> T createMockElement(final Class<T> aClass, final Module module, final boolean physical) {
    final XmlFile file = (XmlFile)myElementFactory.createFileFromText("a.xml", StdFileTypes.XML, "", 0, physical);
    final DomFileElementImpl<T> fileElement = getFileElement(file, aClass, "root");
    fileElement.putUserData(MODULE, module);
    fileElement.putUserData(MOCK, new Object());
    return fileElement.getRootElement();
  }

  public final boolean isMockElement(DomElement element) {
    final DomFileElement<?> root = element.getRoot();
    return root.getUserData(MOCK) != null;
  }

  public final <T extends DomElement> T createStableValue(final Factory<T> provider) {
    final T initial = provider.create();
    final InvocationHandler handler = new StableInvocationHandler<T>(initial, provider);
    final Set<Class> intf = new HashSet<Class>();
    intf.addAll(Arrays.asList(initial.getClass().getInterfaces()));
    intf.add(StableElement.class);
    return AdvancedProxy.createProxy((Class<? extends T>)initial.getClass().getSuperclass(), intf.toArray(new Class[intf.size()]), handler,
                                     Collections.<JavaMethodSignature>emptySet());
  }

  public final void registerFileDescription(final DomFileDescription description, Disposable parentDisposable) {
    registerFileDescription(description);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myFileDescriptions.remove(description);
      }
    });
  }

  public final void registerFileDescription(DomFileDescription description) {
    myFileDescriptions.add(description);
    final Class<? extends DomElement> rootClass = description.getRootElementClass();
    final GenericInfoImpl info = getGenericInfo(rootClass);

    final Set<String> tagNames = info.getReferenceTagNames();
    if (!tagNames.isEmpty()) {
      myReferenceProvidersRegistry.registerXmlTagReferenceProvider(tagNames.toArray(new String[tagNames.size()]),
                                                                   new MyElementFilter(rootClass), true, myGenericValueReferenceProvider);
    }

    final Set<String> attributeNames = info.getReferenceAttributeNames();
    if (!attributeNames.isEmpty()) {
      myReferenceProvidersRegistry.registerXmlAttributeValueReferenceProvider(attributeNames.toArray(new String[attributeNames.size()]),
                                                                              new MyElementFilter(rootClass), true,
                                                                              myGenericValueReferenceProvider);
    }

  }

  @Nullable
  private DomFileDescription findFileDescription(DomElement element) {
    XmlFile file = element.getRoot().getFile();
    final DomFileDescription description = file.getUserData(CACHED_FILE_DESCRIPTION);
    if (description != null) {
      return description;
    }

    for (final DomFileDescription fileDescription : myFileDescriptions) {
      if (fileDescription.isMyFile(file)) {
        file.putUserData(CACHED_FILE_DESCRIPTION, fileDescription);
        return fileDescription;
      }
    }
    return null;
  }

  public final DomElement getResolvingScope(GenericDomValue element) {
    final DomFileDescription description = findFileDescription(element);
    return description == null ? element.getRoot() : description.getResolveScope(element);
  }

  public final DomElement getIdentityScope(DomElement element) {
    final DomFileDescription description = findFileDescription(element);
    return description == null ? element.getParent() : description.getIdentityScope(element);
  }

  public final void clearCaches() {
    myCachedImplementationClasses.clear();
  }

  private class MyElementFilter implements ElementFilter {
    private final Class<? extends DomElement> myRootClass;


    public MyElementFilter(final Class<? extends DomElement> rootClass) {
      myRootClass = rootClass;
    }

    public boolean isAcceptable(Object element, PsiElement context) {
      if (element instanceof XmlElement) {
        final DomRootInvocationHandler handler = getDomFileElement((XmlFile)((PsiElement)element).getContainingFile());
        if (handler != null && myRootClass.isAssignableFrom(DomUtil.getRawType(handler.getDomElementType()))) {
          return true;
        }
      }
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }
}