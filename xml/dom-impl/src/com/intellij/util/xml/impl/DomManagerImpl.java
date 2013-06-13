/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.DomSupportEnabled;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.semantic.SemKey;
import com.intellij.semantic.SemService;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import net.sf.cglib.proxy.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public final class DomManagerImpl extends DomManager {
  private static final Key<Object> MOCK = Key.create("MockElement");

  static final Key<WeakReference<DomFileElementImpl>> CACHED_FILE_ELEMENT = Key.create("CACHED_FILE_ELEMENT");
  static final Key<DomFileDescription> MOCK_DESCRIPTION = Key.create("MockDescription");
  static final SemKey<FileDescriptionCachedValueProvider> FILE_DESCRIPTION_KEY = SemKey.createKey("FILE_DESCRIPTION_KEY");
  static final SemKey<DomInvocationHandler> DOM_HANDLER_KEY = SemKey.createKey("DOM_HANDLER_KEY");
  static final SemKey<IndexedElementInvocationHandler> DOM_INDEXED_HANDLER_KEY = DOM_HANDLER_KEY.subKey("DOM_INDEXED_HANDLER_KEY");
  static final SemKey<CollectionElementInvocationHandler> DOM_COLLECTION_HANDLER_KEY = DOM_HANDLER_KEY.subKey("DOM_COLLECTION_HANDLER_KEY");
  static final SemKey<CollectionElementInvocationHandler> DOM_CUSTOM_HANDLER_KEY = DOM_HANDLER_KEY.subKey("DOM_CUSTOM_HANDLER_KEY");
  static final SemKey<AttributeChildInvocationHandler> DOM_ATTRIBUTE_HANDLER_KEY = DOM_HANDLER_KEY.subKey("DOM_ATTRIBUTE_HANDLER_KEY");

  private final EventDispatcher<DomEventListener> myListeners = EventDispatcher.create(DomEventListener.class);
  private final GenericValueReferenceProvider myGenericValueReferenceProvider = new GenericValueReferenceProvider();

  private final Project myProject;
  private final SemService mySemService;
  private final ConverterManager myConverterManager;
  private final DomApplicationComponent myApplicationComponent;
  private final PsiFileFactory myFileFactory;
  private final ProjectFileIndex myFileIndex;

  private long myModificationCount;
  private boolean myChanging;

  public DomManagerImpl(Project project,
                        final XmlAspect xmlAspect,
                        SemService semService,
                        ConverterManager converterManager,
                        DomApplicationComponent appComponent,
                        PsiFileFactory fileFactory,
                        ProjectFileIndex fileIndex) {
    myProject = project;
    mySemService = semService;
    myConverterManager = converterManager;
    myApplicationComponent = appComponent;
    myFileFactory = fileFactory;
    myFileIndex = fileIndex;

    PomModel pomModel = PomManager.getModel(project);
    pomModel.addModelListener(new PomModelListener() {
      public void modelChanged(PomModelEvent event) {
        final XmlChangeSet changeSet = (XmlChangeSet)event.getChangeSet(xmlAspect);
        if (changeSet != null && !myChanging) {
          for (XmlFile file : changeSet.getChangedFiles()) {
            DomFileElementImpl<DomElement> element = getCachedFileElement(file);
            if (element != null) {
              fireEvent(new DomEvent(element, false));
            }
          }
        }
      }

      public boolean isAspectChangeInteresting(PomModelAspect aspect) {
        return xmlAspect.equals(aspect);
      }
    }, project);

    Runnable setupVfsListeners = new Runnable() {
      public void run() {
        final VirtualFileAdapter listener = new VirtualFileAdapter() {
          private final List<DomEvent> myDeletionEvents = new SmartList<DomEvent>();

          public void contentsChanged(VirtualFileEvent event) {
            if (event.isFromSave()) return;

            processVfsChange(event.getFile());
          }

          public void fileCreated(VirtualFileEvent event) {
            processVfsChange(event.getFile());
          }

          public void beforeFileDeletion(final VirtualFileEvent event) {
            if (!myProject.isDisposed()) {
              beforeFileDeletion(event.getFile());
            }
          }

          private void beforeFileDeletion(final VirtualFile file) {
            if (file.isDirectory() && file instanceof NewVirtualFile) {
              for (final VirtualFile child : ((NewVirtualFile)file).getCachedChildren()) {
                beforeFileDeletion(child);
              }
              return;
            }

            if (file.isValid() && StdFileTypes.XML.equals(file.getFileType())) {
              final PsiFile psiFile = getCachedPsiFile(file);
              if (psiFile instanceof XmlFile) {
                Collections.addAll(myDeletionEvents, recomputeFileElement((XmlFile)psiFile));
              }
            }
          }

          public void fileDeleted(VirtualFileEvent event) {
            if (!myDeletionEvents.isEmpty()) {
              if (!myProject.isDisposed()) {
                for (DomEvent domEvent : myDeletionEvents) {
                  fireEvent(domEvent);
                }
              }
              myDeletionEvents.clear();
            }
          }

          public void propertyChanged(VirtualFilePropertyEvent event) {
            final VirtualFile file = event.getFile();
            if (!file.isDirectory() && VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
              processVfsChange(file);
            }
          }
        };
        VirtualFileManager.getInstance().addVirtualFileListener(listener, myProject);
      }
    };

    StartupManager startupManager = StartupManager.getInstance(project);
    if (!((StartupManagerEx)startupManager).startupActivityPassed()) {
      startupManager.registerStartupActivity(setupVfsListeners);
    }
    else {
      setupVfsListeners.run();
    }
  }

  private void processVfsChange(final VirtualFile file) {
    processFileOrDirectoryChange(file);
  }

  public long getPsiModificationCount() {
    return PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount();
  }

  public <T extends DomInvocationHandler> void cacheHandler(SemKey<T> key, XmlElement element, T handler) {
    mySemService.setCachedSemElement(key, element, handler);
  }

  private void processFileChange(final VirtualFile file) {
    if (StdFileTypes.XML != file.getFileType()) return;
    processFileChange(getCachedPsiFile(file));
  }

  private PsiFile getCachedPsiFile(VirtualFile file) {
    return ((PsiManagerEx)PsiManager.getInstance(myProject)).getFileManager().getCachedPsiFile(file);
  }

  private void processFileChange(final PsiFile file) {
    if (file != null && StdFileTypes.XML.equals(file.getFileType()) && file instanceof XmlFile) {
      for (final DomEvent event : recomputeFileElement((XmlFile)file)) {
        fireEvent(event);
      }
    }
  }

  static DomEvent[] recomputeFileElement(final XmlFile file) {
    final DomFileElementImpl oldElement = getCachedFileElement(file);
    return oldElement == null ? DomEvent.EMPTY_ARRAY : new DomEvent[]{new DomEvent(oldElement, false)};
  }

  private void processDirectoryChange(final VirtualFile directory) {
    for (final VirtualFile file : directory.getChildren()) {
      processFileOrDirectoryChange(file);
    }
  }

  private void processFileOrDirectoryChange(final VirtualFile file) {
    if (!myFileIndex.isInContent(file)) return;
    if (!file.isDirectory()) {
      processFileChange(file);
    } else {
      processDirectoryChange(file);
    }
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static DomManagerImpl getDomManager(Project project) {
    return (DomManagerImpl)DomManager.getDomManager(project);
  }

  public void addDomEventListener(DomEventListener listener, Disposable parentDisposable) {
    myListeners.addListener(listener, parentDisposable);
  }

  public final ConverterManager getConverterManager() {
    return myConverterManager;
  }

  public final void addPsiReferenceFactoryForClass(Class clazz, PsiReferenceFactory psiReferenceFactory) {
    myGenericValueReferenceProvider.addReferenceProviderForClass(clazz, psiReferenceFactory);
  }

  public final ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  final void fireEvent(DomEvent event) {
    if (mySemService.isInsideAtomicChange()) return;
    myModificationCount++;
    myListeners.getMulticaster().eventOccured(event);
  }

  public final DomGenericInfo getGenericInfo(final Type type) {
    return myApplicationComponent.getStaticGenericInfo(type);
  }

  @Nullable
  public static DomInvocationHandler getDomInvocationHandler(DomElement proxy) {
    if (proxy instanceof DomFileElement) {
      return null;
    }
    if (proxy instanceof DomInvocationHandler) {
      return (DomInvocationHandler)proxy;
    }
    final InvocationHandler handler = AdvancedProxy.getInvocationHandler(proxy);
    if (handler instanceof StableInvocationHandler) {
      final DomElement element = ((StableInvocationHandler<DomElement>)handler).getWrappedElement();
      return element == null ? null : getDomInvocationHandler(element);
    }
    if (handler instanceof DomInvocationHandler) {
      return (DomInvocationHandler)handler;
    }
    return null;
  }

  @NotNull
  public static DomInvocationHandler getNotNullHandler(DomElement proxy) {
    DomInvocationHandler handler = getDomInvocationHandler(proxy);
    if (handler == null) {
      throw new AssertionError("null handler for " + proxy);
    }
    return handler;
  }

  public static StableInvocationHandler getStableInvocationHandler(Object proxy) {
    return (StableInvocationHandler)AdvancedProxy.getInvocationHandler(proxy);
  }

  public DomApplicationComponent getApplicationComponent() {
    return myApplicationComponent;
  }

  public final Project getProject() {
    return myProject;
  }

  @NotNull
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(final XmlFile file, final Class<T> aClass, String rootTagName) {
    //noinspection unchecked
    if (file.getUserData(MOCK_DESCRIPTION) == null) {
      file.putUserData(MOCK_DESCRIPTION, new MockDomFileDescription<T>(aClass, rootTagName, file));
      mySemService.clearCache();
    }
    final DomFileElementImpl<T> fileElement = getFileElement(file);
    assert fileElement != null;
    return fileElement;
  }


  @SuppressWarnings({"unchecked"})
  @NotNull
  final <T extends DomElement> FileDescriptionCachedValueProvider<T> getOrCreateCachedValueProvider(final XmlFile xmlFile) {
    return mySemService.getSemElement(FILE_DESCRIPTION_KEY, xmlFile);
  }

  public final Set<DomFileDescription> getFileDescriptions(String rootTagName) {
    return myApplicationComponent.getFileDescriptions(rootTagName);
  }

  public final Set<DomFileDescription> getAcceptingOtherRootTagNameDescriptions() {
    return myApplicationComponent.getAcceptingOtherRootTagNameDescriptions();
  }

  @NotNull
  @NonNls
  public final String getComponentName() {
    return getClass().getName();
  }

  final void runChange(Runnable change) {
    final boolean b = setChanging(true);
    try {
      change.run();
    }
    finally {
      setChanging(b);
    }
  }

  final boolean setChanging(final boolean changing) {
    boolean oldChanging = myChanging;
    if (changing) {
      assert !oldChanging;
    }
    myChanging = changing;
    return oldChanging;
  }

  @Nullable
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file) {
    if (file == null) return null;
    if (!(file.getFileType() instanceof DomSupportEnabled)) return null;
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && virtualFile.isDirectory()) return null;
    return this.<T>getOrCreateCachedValueProvider(file).getFileElement();
  }

  @Nullable
  static <T extends DomElement> DomFileElementImpl<T> getCachedFileElement(@NotNull XmlFile file) {
    WeakReference<DomFileElementImpl> ref = file.getUserData(CACHED_FILE_ELEMENT);
    return ref == null ? null : ref.get();
  }

  @Nullable
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file, Class<T> domClass) {
    final DomFileDescription description = getDomFileDescription(file);
    if (description != null && myApplicationComponent.assignabilityCache.isAssignable(domClass, description.getRootElementClass())) {
      return getFileElement(file);
    }
    return null;
  }

  @Nullable
  public final DomElement getDomElement(final XmlTag element) {
    if (myChanging) return null;

    final DomInvocationHandler handler = getDomHandler(element);
    return handler != null ? handler.getProxy() : null;
  }

  @Nullable
  public GenericAttributeValue getDomElement(final XmlAttribute attribute) {
    if (myChanging) return null;

    final AttributeChildInvocationHandler handler = mySemService.getSemElement(DOM_ATTRIBUTE_HANDLER_KEY, attribute);
    return handler == null ? null : (GenericAttributeValue)handler.getProxy();
  }

  @Nullable
  public DomInvocationHandler getDomHandler(final XmlElement tag) {
    if (tag == null) return null;

    List<DomInvocationHandler> cached = mySemService.getCachedSemElements(DOM_HANDLER_KEY, tag);
    if (cached != null && !cached.isEmpty()) {
      return cached.get(0);
    }


    return mySemService.getSemElement(DOM_HANDLER_KEY, tag);
  }

  @Nullable
  public AbstractDomChildrenDescription findChildrenDescription(@NotNull final XmlTag tag, @NotNull final DomElement parent) {
    return findChildrenDescription(tag, getDomInvocationHandler(parent));
  }

  static AbstractDomChildrenDescription findChildrenDescription(final XmlTag tag, final DomInvocationHandler parent) {
    final DomGenericInfoEx info = parent.getGenericInfo();
    return info.findChildrenDescription(parent, tag.getLocalName(), tag.getNamespace(), false, tag.getName());
  }

  public final boolean isDomFile(@Nullable PsiFile file) {
    return file instanceof XmlFile && getFileElement((XmlFile)file) != null;
  }

  @Nullable
  public final DomFileDescription<?> getDomFileDescription(PsiElement element) {
    if (element instanceof XmlElement) {
      final PsiFile psiFile = element.getContainingFile();
      if (psiFile instanceof XmlFile) {
        return getDomFileDescription((XmlFile)psiFile);
      }
    }
    return null;
  }

  public final <T extends DomElement> T createMockElement(final Class<T> aClass, final Module module, final boolean physical) {
    final XmlFile file = (XmlFile)myFileFactory.createFileFromText("a.xml", StdFileTypes.XML, "", (long)0, physical);
    file.putUserData(MOCK_ELEMENT_MODULE, module);
    file.putUserData(MOCK, new Object());
    return getFileElement(file, aClass, "I_sincerely_hope_that_nobody_will_have_such_a_root_tag_name").getRootElement();
  }

  public final boolean isMockElement(DomElement element) {
    return DomUtil.getFile(element).getUserData(MOCK) != null;
  }

  public final <T extends DomElement> T createStableValue(final Factory<T> provider) {
    return createStableValue(provider, new Condition<T>() {
      public boolean value(T t) {
        return t.isValid();
      }
    });
  }

  public final <T> T createStableValue(final Factory<T> provider, final Condition<T> validator) {
    final T initial = provider.create();
    assert initial != null;
    final StableInvocationHandler handler = new StableInvocationHandler<T>(initial, provider, validator);

    final Set<Class> intf = new HashSet<Class>();
    ContainerUtil.addAll(intf, initial.getClass().getInterfaces());
    intf.add(StableElement.class);
    //noinspection unchecked

    return (T)AdvancedProxy.createProxy(initial.getClass().getSuperclass(), intf.toArray(new Class[intf.size()]),
                                        handler);
  }

  public final <T extends DomElement> void registerFileDescription(final DomFileDescription<T> description, Disposable parentDisposable) {
    registerFileDescription(description);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        getFileDescriptions(description.getRootTagName()).remove(description);
        getAcceptingOtherRootTagNameDescriptions().remove(description);
      }
    });
  }

  public final void registerFileDescription(final DomFileDescription description) {
    mySemService.clearCache();

    myApplicationComponent.registerFileDescription(description);
  }

  @NotNull
  public final DomElement getResolvingScope(GenericDomValue element) {
    final DomFileDescription description = DomUtil.getFileElement(element).getFileDescription();
    return description.getResolveScope(element);
  }

  @Nullable
  public final DomElement getIdentityScope(DomElement element) {
    final DomFileDescription description = DomUtil.getFileElement(element).getFileDescription();
    return description.getIdentityScope(element);
  }

  public TypeChooserManager getTypeChooserManager() {
    return myApplicationComponent.getTypeChooserManager();
  }

  public long getModificationCount() {
    return myModificationCount + PsiManager.getInstance(myProject).getModificationTracker().getOutOfCodeBlockModificationCount();
  }

  public void performAtomicChange(@NotNull Runnable change) {
    mySemService.performAtomicChange(change);
    if (!mySemService.isInsideAtomicChange()) {
      myModificationCount++;
    }
  }

  public SemService getSemService() {
    return mySemService;
  }
}
