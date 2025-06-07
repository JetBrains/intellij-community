// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.google.gson.Gson;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.jetbrains.python.PythonPluginDisposable;
import com.jetbrains.python.sdk.flavors.PyFlavorAndData;
import com.jetbrains.python.sdk.flavors.PyFlavorData;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Python-specific stuff each SDK must have. Use ``PySdkExt.getOrCreateAdditionalData`` to obtain it.
 */
// TODO: Use new annotation-based API to save data instead of legacy manual save
public class PythonSdkAdditionalData implements SdkAdditionalData {
  private static final @NonNls String PATHS_ADDED_BY_USER_ROOT = "PATHS_ADDED_BY_USER_ROOT";
  private static final @NonNls String PATH_ADDED_BY_USER = "PATH_ADDED_BY_USER";
  private static final @NonNls String PATHS_REMOVED_BY_USER_ROOT = "PATHS_REMOVED_BY_USER_ROOT";
  private static final @NonNls String PATH_REMOVED_BY_USER = "PATH_REMOVED_BY_USER";
  private static final @NonNls String PATHS_TO_TRANSFER_ROOT = "PATHS_TO_TRANSFER_ROOT";
  private static final @NonNls String PATH_TO_TRANSFER = "PATH_TO_TRANSFER";
  private static final @NonNls String ASSOCIATED_PROJECT_PATH = "ASSOCIATED_PROJECT_PATH";
  private static final @NonNls String SDK_UUID_FIELD_NAME = "SDK_UUID";

  private static final @NonNls String FLAVOR_ID = "FLAVOR_ID";
  private static final @NonNls String FLAVOR_DATA = "FLAVOR_DATA";

  private final VirtualFilePointerContainer myAddedPaths;
  private final VirtualFilePointerContainer myExcludedPaths;
  private final VirtualFilePointerContainer myPathsToTransfer;
  private @NotNull UUID myUUID = UUID.randomUUID();

  private PyFlavorAndData<?, ?> myFlavorAndData;
  private String myAssociatedModulePath;

  private final Gson myGson = new Gson();


  public PythonSdkAdditionalData() {
    this((PyFlavorAndData<?, ?>)null);
  }

  /**
   * @deprecated Use constructor with data. This ctor only supports flavours with empty data
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  @Deprecated(forRemoval = true)
  public PythonSdkAdditionalData(@Nullable PythonSdkFlavor<?> flavor) {
    this(flavor == null
         ? PyFlavorAndData.getUNKNOWN_FLAVOR_DATA()
         : new PyFlavorAndData(PyFlavorData.Empty.INSTANCE, ensureSupportsEmptyData(flavor)));
  }

  private static @NotNull PythonSdkFlavor<?> ensureSupportsEmptyData(@NotNull PythonSdkFlavor<?> flavor) {
    if (!flavor.supportsEmptyData()) {
      throw new IllegalArgumentException(flavor.getName() + " can't be created without additional data");
    }
    return flavor;
  }

  public PythonSdkAdditionalData(@Nullable PyFlavorAndData<?, ?> flavorAndData) {
    myFlavorAndData = (flavorAndData != null ? flavorAndData : PyFlavorAndData.getUNKNOWN_FLAVOR_DATA());
    myAddedPaths = VirtualFilePointerManager.getInstance().createContainer(PythonPluginDisposable.getInstance());
    myExcludedPaths = VirtualFilePointerManager.getInstance().createContainer(PythonPluginDisposable.getInstance());
    myPathsToTransfer = VirtualFilePointerManager.getInstance().createContainer(PythonPluginDisposable.getInstance());
  }

  protected PythonSdkAdditionalData(@NotNull PythonSdkAdditionalData from) {
    myAddedPaths = from.myAddedPaths.clone(PythonPluginDisposable.getInstance());
    myExcludedPaths = from.myExcludedPaths.clone(PythonPluginDisposable.getInstance());
    myPathsToTransfer = from.myPathsToTransfer.clone(PythonPluginDisposable.getInstance());
    myAssociatedModulePath = from.myAssociatedModulePath;
    myFlavorAndData = from.myFlavorAndData;
    myUUID = from.myUUID;
  }

  /**
   * Temporary hack to deal with leagcy conda. Use constructor instead
   */
  @ApiStatus.Internal
  public final void changeFlavorAndData(@NotNull PyFlavorAndData<?, ?> flavorAndData) {
    this.myFlavorAndData = flavorAndData;
  }

  /**
   * Persistent UUID of SDK.  Could be used to point to "this particular" SDK.
   */
  @ApiStatus.Internal
  public final @NotNull UUID getUUID() {
    return myUUID;
  }

  public @NotNull PythonSdkAdditionalData copy() {
    return new PythonSdkAdditionalData(this);
  }

  public final void setAddedPathsFromVirtualFiles(@NotNull Set<VirtualFile> addedPaths) {
    myAddedPaths.clear();
    for (VirtualFile file : addedPaths) {
      myAddedPaths.add(file);
    }
  }

  @ApiStatus.Internal
  public final void setExcludedPathsFromVirtualFiles(@NotNull Set<VirtualFile> addedPaths) {
    myExcludedPaths.clear();
    for (VirtualFile file : addedPaths) {
      myExcludedPaths.add(file);
    }
  }

  @ApiStatus.Internal
  public final void setPathsToTransferFromVirtualFiles(@NotNull Set<VirtualFile> addedPaths) {
    myPathsToTransfer.clear();
    for (VirtualFile file : addedPaths) {
      myPathsToTransfer.add(file);
    }
  }

  @ApiStatus.Internal
  public final String getAssociatedModulePath() {
    return myAssociatedModulePath;
  }


  /**
   * Be sure to use {@link com.intellij.openapi.projectRoots.SdkModificator} to save changes
   */
  @ApiStatus.Internal
  public final void setAssociatedModulePath(@Nullable String modulePath) {
    myAssociatedModulePath = modulePath == null ? null : FileUtil.toSystemIndependentName(modulePath);
  }

  public void save(final @NotNull Element rootElement) {
    savePaths(rootElement, myAddedPaths, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER);
    savePaths(rootElement, myExcludedPaths, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER);
    savePaths(rootElement, myPathsToTransfer, PATHS_TO_TRANSFER_ROOT, PATH_TO_TRANSFER);

    if (myAssociatedModulePath != null) {
      rootElement.setAttribute(ASSOCIATED_PROJECT_PATH, myAssociatedModulePath);
    }
    rootElement.setAttribute(SDK_UUID_FIELD_NAME, myUUID.toString());
    JDOMExternalizer.write(rootElement, FLAVOR_ID, myFlavorAndData.getFlavor().getUniqueId());
    JDOMExternalizer.write(rootElement, FLAVOR_DATA, myGson.toJson(myFlavorAndData.getData(), myFlavorAndData.getDataClass()));
  }

  private static void savePaths(Element rootElement, VirtualFilePointerContainer paths, String root, String element) {
    for (String addedPath : paths.getUrls()) {
      final Element child = new Element(root);
      child.setAttribute(element, addedPath);
      rootElement.addContent(child);
    }
  }

  public final @NotNull PythonSdkFlavor<?> getFlavor() {
    return myFlavorAndData.getFlavor();
  }

  @ApiStatus.Internal

  public final @NotNull PyFlavorAndData<?, ?> getFlavorAndData() {
    return myFlavorAndData;
  }

  @ApiStatus.Internal

  public static @NotNull PythonSdkAdditionalData loadFromElement(@Nullable Element element) {
    final PythonSdkAdditionalData data = new PythonSdkAdditionalData();
    data.load(element);
    return data;
  }

  public void load(@Nullable Element element) {
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER), myAddedPaths);
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER), myExcludedPaths);
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_TO_TRANSFER_ROOT, PATH_TO_TRANSFER), myPathsToTransfer);
    if (element != null) {
      myAssociatedModulePath = element.getAttributeValue(ASSOCIATED_PROJECT_PATH);
      var uuidStr = element.getAttributeValue(SDK_UUID_FIELD_NAME);
      if (uuidStr != null) {
        myUUID = UUID.fromString(uuidStr);
      }
      var flavorId = JDOMExternalizer.readString(element, FLAVOR_ID);
      if (flavorId != null) {
        var flavorOpt = PythonSdkFlavor.getApplicableFlavors(true).stream().filter(f -> f.getUniqueId().equals(flavorId)).findFirst();
        if (flavorOpt.isPresent()) {
          setFlavorFromConfig(element, flavorOpt.get());
        }
        else {
          myFlavorAndData = new PyFlavorAndData<>(PyFlavorData.Empty.INSTANCE, PythonSdkFlavor.UnknownFlavor.INSTANCE);
        }
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void setFlavorFromConfig(@NotNull Element element, @NotNull PythonSdkFlavor<?> flavor) {
    var flavorData = myGson.fromJson(JDOMExternalizer.readString(element, FLAVOR_DATA), flavor.getFlavorDataClass());
    myFlavorAndData = new PyFlavorAndData(flavorData, flavor);
  }

  private static void collectPaths(@NotNull List<String> paths, VirtualFilePointerContainer container) {
    for (String path : paths) {
      if (StringUtil.isEmpty(path)) continue;
      final String protocol = VirtualFileManager.extractProtocol(path);
      final String url = protocol != null ? path : VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
      container.add(url);
    }
  }

  @ApiStatus.Internal

  public final Set<VirtualFile> getAddedPathFiles() {
    return getPathsAsVirtualFiles(myAddedPaths);
  }

  @ApiStatus.Internal

  public final Set<VirtualFile> getExcludedPathFiles() {
    return getPathsAsVirtualFiles(myExcludedPaths);
  }

  /**
   * @see com.jetbrains.python.sdk.PyTransferredSdkRootsKt#getPathsToTransfer(Sdk)
   */

  @ApiStatus.Internal
  public final @NotNull Set<VirtualFile> getPathsToTransfer() {
    return getPathsAsVirtualFiles(myPathsToTransfer);
  }

  private static Set<VirtualFile> getPathsAsVirtualFiles(VirtualFilePointerContainer paths) {
    Set<VirtualFile> ret = new LinkedHashSet<>();
    Collections.addAll(ret, paths.getFiles());
    return ret;
  }
}
