package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 *  @author dsl
 */
abstract class LibraryOrderEntryBaseImpl extends OrderEntryBaseImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryBaseImpl");
  protected final RootModelImpl myRootModel;
  private final Map<OrderRootType, VirtualFilePointerContainer> myRootContainers;
  private final MyRootSetChangedListener myRootSetChangedListener = new MyRootSetChangedListener();
  private RootProvider myCurrentlySubscribedRootProvider = null;
  protected final ProjectRootManagerImpl myProjectRootManagerImpl;

  LibraryOrderEntryBaseImpl(RootModelImpl rootModel, ProjectRootManagerImpl instanceImpl, VirtualFilePointerManager filePointerManager) {
    super(rootModel);
    myRootModel = rootModel;
    myRootContainers = new HashMap<OrderRootType, VirtualFilePointerContainer>();
    for (OrderRootType type : OrderRootType.ALL_TYPES) {
      myRootContainers.put(type, filePointerManager.createContainer(myRootModel.pointerFactory()));
    }
    myProjectRootManagerImpl = instanceImpl;
  }

  protected final void init(RootProvider rootProvider) {
    if (rootProvider == null) return;
    updatePathsFromProviderAndSubscribe(rootProvider);
  }

  protected void updatePathsFromProviderAndSubscribe(final RootProvider rootProvider) {
    updatePathsFromProvider(rootProvider);
    resubscribe(rootProvider);
  }

  private void updatePathsFromProvider(final RootProvider rootProvider) {
    final OrderRootType[] allTypes = OrderRootType.ALL_TYPES;
    for (OrderRootType type : allTypes) {
      final VirtualFilePointerContainer container = myRootContainers.get(type);
      container.clear();
      if (rootProvider != null) {
        final String[] urls = rootProvider.getUrls(type);
        for (String url : urls) {
          container.add(url);
        }
      }
    }
  }

  private boolean needUpdateFromProvider(final RootProvider rootProvider) {
    final OrderRootType[] allTypes = OrderRootType.ALL_TYPES;
    for (OrderRootType type : allTypes) {
      final VirtualFilePointerContainer container = myRootContainers.get(type);
      final String[] urls = container.getUrls();
      final String[] providerUrls = rootProvider.getUrls(type);
      if (!Arrays.equals(urls, providerUrls)) return true;
    }
    return false;
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    return myRootContainers.get(type).getDirectories();
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    LOG.assertTrue(!myRootModel.getModule().isDisposed());
    return myRootContainers.get(type).getUrls();
  }

  public final Module getOwnerModule() {
    return myRootModel.getModule();
  }

  protected void updateFromRootProviderAndSubscribe(RootProvider wrapper) {
    myRootModel.fireBeforeExternalChange();
    updatePathsFromProviderAndSubscribe(wrapper);
    myRootModel.fireAfterExternalChange();
  }

  private void updateFromRootProvider(RootProvider wrapper) {
    myRootModel.fireBeforeExternalChange();
    updatePathsFromProvider(wrapper);
    myRootModel.fireAfterExternalChange();
  }

  private void resubscribe(RootProvider wrapper) {
    unsubscribe();
    subscribe(wrapper);
  }

  private void subscribe(RootProvider wrapper) {
    if (wrapper != null) {
      addListenerToWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = wrapper;
  }

  protected void addListenerToWrapper(final RootProvider wrapper,
                                      final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.addRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  private void unsubscribe() {
    if (myCurrentlySubscribedRootProvider != null) {
      final RootProvider wrapper = myCurrentlySubscribedRootProvider;
      removeListenerFromWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = null;
  }

  protected void removeListenerFromWrapper(final RootProvider wrapper,
                                           final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.removeRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  protected void dispose() {
    super.dispose();
    final Collection<VirtualFilePointerContainer> virtualFilePointerContainers = myRootContainers.values();
    for (VirtualFilePointerContainer virtualFilePointerContainer : virtualFilePointerContainers) {
      virtualFilePointerContainer.killAll();
    }
    unsubscribe();
  }

  private class MyRootSetChangedListener implements RootProvider.RootSetChangedListener {

    public MyRootSetChangedListener() {
    }

    public void rootSetChanged(RootProvider wrapper) {
      if (LibraryOrderEntryBaseImpl.this.needUpdateFromProvider(wrapper)) {
        LibraryOrderEntryBaseImpl.this.updateFromRootProvider(wrapper);
      }
    }


  }

}
