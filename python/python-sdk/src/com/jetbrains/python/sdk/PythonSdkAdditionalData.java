// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Python-specific stuff each SDK must have. Use ``PySdkExt.getOrCreateAdditionalData`` to obtain it.
 */
// TODO: Use new annotation-based API to save data instead of legacy manual save
public class PythonSdkAdditionalData implements SdkAdditionalData {
  private static final PyFlavorAndData<?, ?> UNKNOWN_DATA = new PyFlavorAndData<>(PyFlavorData.Empty.INSTANCE,
                                                                                  PythonSdkFlavor.UnknownFlavor.INSTANCE);
  @ApiStatus.Internal
  public static final Path REQUIREMENT_TXT_DEFAULT = Path.of("requirements.txt");

  private static final @NonNls String PATHS_ADDED_BY_USER_ROOT = "PATHS_ADDED_BY_USER_ROOT";
  private static final @NonNls String PATH_ADDED_BY_USER = "PATH_ADDED_BY_USER";
  private static final @NonNls String PATHS_REMOVED_BY_USER_ROOT = "PATHS_REMOVED_BY_USER_ROOT";
  private static final @NonNls String PATH_REMOVED_BY_USER = "PATH_REMOVED_BY_USER";
  private static final @NonNls String PATHS_TO_TRANSFER_ROOT = "PATHS_TO_TRANSFER_ROOT";
  private static final @NonNls String PATH_TO_TRANSFER = "PATH_TO_TRANSFER";
  private static final @NonNls String ASSOCIATED_PROJECT_PATH = "ASSOCIATED_PROJECT_PATH";
  private static final @NonNls String ASSOCIATED_REQUIRED_TXT_PATH = "ASSOCIATED_REQUIRED_TXT_PATH";
  private static final @NonNls String REQUIREMENTS_FILE = "REQUIREMENTS_FILE";
  private static final @NonNls String WORKING_DIRECTORY = "WORKING_DIRECTORY";
  private static final @NonNls String SDK_UUID_FIELD_NAME = "SDK_UUID";

  private static final @NonNls String FLAVOR_ID = "FLAVOR_ID";
  private static final @NonNls String FLAVOR_DATA = "FLAVOR_DATA";
  private static final Path EMPTY_WORKING_DIRECTORY = Path.of("");

  private final VirtualFilePointerContainer myAddedPaths;
  private final VirtualFilePointerContainer myExcludedPaths;
  private final VirtualFilePointerContainer myPathsToTransfer;
  private @NotNull UUID myUUID = UUID.randomUUID();

  private PyFlavorAndData<?, ?> myFlavorAndData;
  private String myAssociatedModulePath;
  private String myRequirementsFile;
  private Path myLegacyRequiredTxtPath;
  private @NotNull Path myWorkingDirectory = EMPTY_WORKING_DIRECTORY;

  private final Gson myGson = new GsonBuilder().registerTypeAdapter(Path.class, new PathSerializer()).create();

  private PythonSdkAdditionalData() {
    this(null);
  }

  /**
   * @deprecated Use constructor with data and working directory, all SDKs without working directory are considered invalid.
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public PythonSdkAdditionalData(@Nullable PyFlavorAndData<?, ?> flavorAndData) {
    myFlavorAndData = (flavorAndData != null ? flavorAndData : UNKNOWN_DATA);
    myAddedPaths = VirtualFilePointerManager.getInstance().createContainer(PythonPluginDisposable.getInstance());
    myExcludedPaths = VirtualFilePointerManager.getInstance().createContainer(PythonPluginDisposable.getInstance());
    myPathsToTransfer = VirtualFilePointerManager.getInstance().createContainer(PythonPluginDisposable.getInstance());
  }

  public PythonSdkAdditionalData(@Nullable PyFlavorAndData<?, ?> flavorAndData, @NotNull Path workingDirectory) {
    this(flavorAndData);
    setWorkingDirectory(workingDirectory);
  }

  /**
   * Persistent UUID of SDK.  Could be used to point to "this particular" SDK.
   */
  @ApiStatus.Internal
  public final @NotNull UUID getUUID() {
    return myUUID;
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

  /**
   * ONLY FOR INTERNAL USE!
   * For external usage use the requirements file SDK utilities.
   * Be sure to use {@link com.intellij.openapi.projectRoots.SdkModificator} to save changes
   */
  @ApiStatus.Internal
  public final @Nullable String getRequirementsFile() {
    return myRequirementsFile;
  }

  /**
   * ONLY FOR INTERNAL USE!
   * For external usage use the requirements file SDK utilities.
   * Be sure to use {@link com.intellij.openapi.projectRoots.SdkModificator} to save changes
   */
  @ApiStatus.Internal
  public final void setRequirementsFile(@Nullable String requirementsFile) {
    myRequirementsFile = requirementsFile;
  }

  @ApiStatus.Internal
  public final @Nullable Path getRequirementsPath() {
    return myRequirementsFile == null ? null : getWorkingDirectory().resolve(myRequirementsFile);
  }

  @ApiStatus.Internal
  public final void setRequirementsPath(@Nullable Path requirementsPath) {
    if (requirementsPath == null) {
      setRequirementsFile(null);
      return;
    }

    Path resolvedPath = requirementsPath.isAbsolute() ? requirementsPath : getWorkingDirectory().resolve(requirementsPath);
    Path fileName = resolvedPath.getFileName();
    if (fileName == null || fileName.toString().isBlank()) {
      throw new IllegalArgumentException("Python SDK requirements file name must not be empty");
    }
    Path parent = resolvedPath.getParent();
    if (parent != null) {
      setWorkingDirectory(parent);
    }
    setRequirementsFile(fileName.toString());
  }

  @ApiStatus.Internal
  public final @NotNull Path getWorkingDirectory() {
    return myWorkingDirectory;
  }

  @ApiStatus.Internal
  public final boolean hasValidWorkingDirectory() {
    return !Objects.equals(myWorkingDirectory, EMPTY_WORKING_DIRECTORY);
  }

  @ApiStatus.Internal
  public final void setWorkingDirectory(@NotNull Path workingDirectory) {
    myWorkingDirectory = workingDirectory;
    synchronizeFlavorWorkingDirectory();
  }

  public void save(final @NotNull Element rootElement) {
    savePaths(rootElement, myAddedPaths, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER);
    savePaths(rootElement, myExcludedPaths, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER);
    savePaths(rootElement, myPathsToTransfer, PATHS_TO_TRANSFER_ROOT, PATH_TO_TRANSFER);

    if (myAssociatedModulePath != null) {
      rootElement.setAttribute(ASSOCIATED_PROJECT_PATH, myAssociatedModulePath);
    }

    if (myRequirementsFile != null) {
      rootElement.setAttribute(REQUIREMENTS_FILE, myRequirementsFile);
    }
    if (myLegacyRequiredTxtPath != null) {
      rootElement.setAttribute(ASSOCIATED_REQUIRED_TXT_PATH, myLegacyRequiredTxtPath.toString());
    }

    if (hasValidWorkingDirectory()) {
      rootElement.setAttribute(WORKING_DIRECTORY, myWorkingDirectory.toString());
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
  public final boolean migrateAdditionalData(@Nullable Path fallbackWorkingDirectory) {
    FlavorMigrationResult flavorMigration = migrateFlavorData(myFlavorAndData);
    boolean changed = flavorMigration.changed();

    Path requirementsWorkingDirectory = null;
    if (myRequirementsFile == null) {
      Path legacyRequirementsPath = resolveLegacyRequirementsPath();
      if (legacyRequirementsPath != null && legacyRequirementsPath.getFileName() != null) {
        myRequirementsFile = legacyRequirementsPath.getFileName().toString();
        requirementsWorkingDirectory = legacyRequirementsPath.getParent();
        changed = true;
      }
    }

    if (!hasValidWorkingDirectory()) {
      Path workingDirectory = flavorMigration.workingDirectory();
      if (workingDirectory == null) workingDirectory = requirementsWorkingDirectory;
      if (workingDirectory == null) workingDirectory = fallbackWorkingDirectory;

      if (workingDirectory != null && !workingDirectory.toString().isBlank()) {
        setWorkingDirectory(workingDirectory);
        changed = true;
      }
    }
    if (hasValidWorkingDirectory()) {
      changed |= synchronizeFlavorWorkingDirectory();
    }

    return changed;
  }

  @ApiStatus.Internal

  public static @NotNull PythonSdkAdditionalData loadFromElement(@Nullable Element element) {
    final PythonSdkAdditionalData data = new PythonSdkAdditionalData();
    data.load(element);
    return data;
  }

  public void load(@Nullable Element element) {
    myWorkingDirectory = EMPTY_WORKING_DIRECTORY;
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER), myAddedPaths);
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER), myExcludedPaths);
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_TO_TRANSFER_ROOT, PATH_TO_TRANSFER), myPathsToTransfer);
    if (element != null) {
      myAssociatedModulePath = element.getAttributeValue(ASSOCIATED_PROJECT_PATH);

      String storedWorkingDirectory = element.getAttributeValue(WORKING_DIRECTORY);
      if (storedWorkingDirectory != null && !storedWorkingDirectory.isBlank()) {
        myWorkingDirectory = Path.of(storedWorkingDirectory);
      }
      myRequirementsFile = element.getAttributeValue(REQUIREMENTS_FILE);
      String legacyRequiredTxtPath = element.getAttributeValue(ASSOCIATED_REQUIRED_TXT_PATH);
      myLegacyRequiredTxtPath = legacyRequiredTxtPath == null ? null : Path.of(legacyRequiredTxtPath);

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

  private @Nullable Path resolveLegacyRequirementsPath() {
    if (myLegacyRequiredTxtPath == null) return null;
    if (!myLegacyRequiredTxtPath.isAbsolute() && myAssociatedModulePath != null) {
      return Path.of(myAssociatedModulePath).resolve(myLegacyRequiredTxtPath);
    }
    return myLegacyRequiredTxtPath;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void setFlavorFromConfig(@NotNull Element element, @NotNull PythonSdkFlavor<?> flavor) {
    var flavorData = myGson.fromJson(JDOMExternalizer.readString(element, FLAVOR_DATA), flavor.getFlavorDataClass());
    myFlavorAndData = new PyFlavorAndData(flavorData, flavor);
  }

  private <D extends PyFlavorData, F extends PythonSdkFlavor<D>> @NotNull FlavorMigrationResult migrateFlavorData(
    @NotNull PyFlavorAndData<D, F> flavorAndData
  ) {
    PythonSdkFlavor.AdditionalDataMigration<D> migration =
      flavorAndData.getFlavor().migrateAdditionalData(this, flavorAndData.getData());
    boolean changed = !Objects.equals(migration.getFlavorData(), flavorAndData.getData());
    if (changed) {
      myFlavorAndData = new PyFlavorAndData<>(migration.getFlavorData(), flavorAndData.getFlavor());
    }
    return new FlavorMigrationResult(migration.getWorkingDirectory(), changed);
  }

  private boolean synchronizeFlavorWorkingDirectory() {
    if (!hasValidWorkingDirectory()) return false;
    return synchronizeFlavorWorkingDirectory(myFlavorAndData, myWorkingDirectory);
  }

  private <D extends PyFlavorData, F extends PythonSdkFlavor<D>> boolean synchronizeFlavorWorkingDirectory(
    @NotNull PyFlavorAndData<D, F> flavorAndData,
    @NotNull Path workingDirectory
  ) {
    D synchronizedData = flavorAndData.getFlavor().withWorkingDirectory(flavorAndData.getData(), workingDirectory);
    if (Objects.equals(synchronizedData, flavorAndData.getData())) return false;
    myFlavorAndData = new PyFlavorAndData<>(synchronizedData, flavorAndData.getFlavor());
    return true;
  }

  private record FlavorMigrationResult(@Nullable Path workingDirectory, boolean changed) {
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

  private static class PathSerializer extends TypeAdapter<@Nullable Path> {
    @Override
    public void write(JsonWriter out, @Nullable Path value) throws IOException {
      if (value == null) {
        out.nullValue();
      }
      else {
        out.value(value.toString());
      }
    }

    @Override
    public @Nullable Path read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }

      return Path.of(in.nextString());
    }
  }
}
