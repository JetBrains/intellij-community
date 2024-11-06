// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Maps;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.FilePropertyKey;
import com.intellij.psi.FilePropertyKeyImpl;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeNodeProcessingResult;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.module.PyModuleService;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Supplier;

public final class PythonLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {
  private static final Key<String> PROJECT_LANGUAGE_LEVEL = new Key<>("python.project.language.level");
  private static final FilePropertyKey<LanguageLevel> KEY =
    FilePropertyKeyImpl.createPersistentEnumKey("python.language.level", "python_language_level_persistence", 3, LanguageLevel.class);

  /* It so happens that no single language level is compatible with more than one other.
     So a map suffices for representation*/
  private static final Map<LanguageLevel, LanguageLevel> COMPATIBLE_LEVELS;

  static {
    Map<LanguageLevel, LanguageLevel> compatLevels = Maps.newEnumMap(LanguageLevel.class);
    addCompatiblePair(compatLevels, LanguageLevel.PYTHON26, LanguageLevel.PYTHON27);
    addCompatiblePair(compatLevels, LanguageLevel.PYTHON33, LanguageLevel.PYTHON34);
    COMPATIBLE_LEVELS = Maps.immutableEnumMap(compatLevels);
  }

  private static void addCompatiblePair(Map<LanguageLevel, LanguageLevel> levels, LanguageLevel l1, LanguageLevel l2) {
    levels.put(l1, l2);
    levels.put(l2, l1);
  }

  private final Map<Module, Sdk> myModuleSdks = new WeakHashMap<>();

  @Override
  public void initExtra(@NotNull Project project) {
    final Map<Module, Sdk> moduleSdks = getPythonModuleSdks(project);

    myModuleSdks.putAll(moduleSdks);
    resetProjectLanguageLevel(project);
    updateSdkLanguageLevels(project, moduleSdks);
    guessLanguageLevelWithCaching(project, moduleSdks::values);
  }

  @Override
  public @NotNull FilePropertyKey<LanguageLevel> getFilePropertyKey() {
    return KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return true;
  }

  @Override
  public @NotNull LanguageLevel getDefaultValue() {
    return LanguageLevel.getDefault();
  }

  @Override
  public @Nullable LanguageLevel getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return null;
  }

  private static @Nullable Sdk getFileSdk(@NotNull Project project, @NotNull VirtualFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null) {
      return PythonSdkUtil.findPythonSdk(module);
    }
    else {
      return findSdkForFileOutsideTheProject(project, file);
    }
  }

  private static @Nullable Sdk findSdkForFileOutsideTheProject(Project project, VirtualFile file) {
    if (file != null) {
      final List<OrderEntry> orderEntries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          return ((JdkOrderEntry)orderEntry).getJdk();
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull LanguageLevel getImmediateValue(@NotNull Module module) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.FORCE_LANGUAGE_LEVEL;
    }

    final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    return PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk);
  }

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file, @NotNull Project project) {
    return false;
  }

  @Override
  public boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    return true;
  }

  private static boolean areLanguageLevelsCompatible(LanguageLevel oldLevel, LanguageLevel newLevel) {
    return oldLevel != null && newLevel != null && COMPATIBLE_LEVELS.get(oldLevel) == newLevel;
  }

  @Override
  public void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull LanguageLevel level) {
    LanguageLevel oldLanguageLevel = KEY.getPersistentValue(fileOrDir);
    boolean changed = KEY.setPersistentValue(fileOrDir, level);
    if (!changed) return;

    if (!areLanguageLevelsCompatible(oldLanguageLevel, level) || !ProjectFileIndex.getInstance(project).isInContent(fileOrDir)) {
      PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, PythonLanguageLevelPusher::isPythonFile);
    }
    if (fileOrDir.isDirectory() || isPythonFile(fileOrDir)) {
      clearSdkPathCache(fileOrDir);
    }
  }

  private static boolean isPythonFile(VirtualFile child) {
    return PythonFileType.INSTANCE.equals(FileTypeRegistry.getInstance().getFileTypeByFileName(child.getNameSequence()));
  }

  private static void clearSdkPathCache(final @NotNull VirtualFile child) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final Sdk sdk = getFileSdk(project, child);
      if (sdk != null) {
        final PythonSdkPathCache pathCache = PythonSdkPathCache.getInstance(project, sdk);
        pathCache.clearCache();
      }
    }
  }

  @Override
  public void afterRootsChanged(final @NotNull Project project) {
    final Map<Module, Sdk> moduleSdks = getPythonModuleSdks(project);
    final boolean needToReparseOpenFiles = ContainerUtil.exists(moduleSdks.entrySet(), entry -> {
      final Module module = entry.getKey();
      final Sdk newSdk = entry.getValue();
      final Sdk oldSdk = myModuleSdks.get(module);
      return myModuleSdks.containsKey(module) && newSdk != oldSdk;
    });

    myModuleSdks.putAll(moduleSdks);
    resetProjectLanguageLevel(project);
    updateSdkLanguageLevels(project, moduleSdks);

    if (needToReparseOpenFiles) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed()) {
          return;
        }
        PythonCodeStyleService.getInstance().reparseOpenEditorFiles(project);
      });
    }
  }

  private static @NotNull Map<@NotNull Module, @NotNull Sdk> getPythonModuleSdks(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.Companion.getInstanceIfDefined(project);
    if (moduleManager == null) {
      return Collections.emptyMap();
    }

    final Map<Module, Sdk> result = new LinkedHashMap<>();
    for (Module module : moduleManager.getModules()) {
      Sdk sdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk != null) {
        result.put(module, sdk);
      }
    }
    return result;
  }

  private void updateSdkLanguageLevels(@NotNull Project project, @NotNull Map<Module, Sdk> moduleSdks) {
    if (moduleSdks.isEmpty()) {
      return;
    }

    new MyDumbModeTask(project, moduleSdks).queue(project);
  }

  private List<Runnable> getRootUpdateTasks(@NotNull Project project, @NotNull Map<Module, Sdk> moduleSdks) {
    final List<Runnable> results = new ArrayList<>();
    for (Map.Entry<Module, Sdk> moduleToSdk : moduleSdks.entrySet()) {
      final Module module = moduleToSdk.getKey();
      if (module.isDisposed()) continue;

      final Sdk sdk = moduleToSdk.getValue();
      final LanguageLevel languageLevel =
        PythonSdkUtil.isDisposed(sdk) ? LanguageLevel.getDefault() : PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk);
      for (VirtualFile root : PyUtil.getSourceRoots(module)) {
        addRootIndexingTask(root, results, project, languageLevel, true);
      }
    }
    final LinkedHashSet<Sdk> distinctSdks = new LinkedHashSet<>(moduleSdks.values());
    for (Sdk sdk : distinctSdks) {
      if (PythonSdkUtil.isDisposed(sdk)) continue;

      final LanguageLevel languageLevel = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk);
      for (VirtualFile root : sdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
        if (!root.isValid() || PyTypeShed.INSTANCE.isInside(root)) {
          continue;
        }
        addRootIndexingTask(root, results, project, languageLevel, false);
      }
    }
    return results;
  }

  private void addRootIndexingTask(@NotNull VirtualFile root,
                                   @NotNull List<Runnable> results,
                                   @NotNull Project project,
                                   @NotNull LanguageLevel languageLevel,
                                   boolean iterateAsContent) {
    final VirtualFile parent = root.getParent();
    final boolean shouldSuppressSizeLimit = parent != null && parent.getName().equals(PythonSdkUtil.SKELETON_DIR_NAME);
    results.add(new UpdateRootTask(project, root, languageLevel, shouldSuppressSizeLimit, iterateAsContent));
  }

  private static @NotNull LanguageLevel guessLanguageLevelWithCaching(@NotNull Project project,
                                                                      @NotNull Supplier<@NotNull Collection<? extends Sdk>> pythonModuleSdks) {
    LanguageLevel languageLevel = LanguageLevel.fromPythonVersion(PROJECT_LANGUAGE_LEVEL.get(project));
    if (languageLevel == null) {
      languageLevel = guessLanguageLevel(pythonModuleSdks.get());
      PROJECT_LANGUAGE_LEVEL.set(project, languageLevel.toPythonVersion());
    }

    return languageLevel;
  }

  private static void resetProjectLanguageLevel(@NotNull Project project) {
    PROJECT_LANGUAGE_LEVEL.set(project, null);
  }

  private static @NotNull LanguageLevel guessLanguageLevel(@NotNull Collection<? extends @NotNull Sdk> pythonModuleSdks) {
    PythonRuntimeService pythonRuntimeService = PythonRuntimeService.getInstance();
    return pythonModuleSdks.stream()
      .distinct()
      .map(sdk -> pythonRuntimeService.getLanguageLevelForSdk(sdk))
      .max(LanguageLevel.VERSION_COMPARATOR)
      .orElse(LanguageLevel.getDefault());
  }

  @ApiStatus.Experimental
  public static @NotNull LanguageLevel getLanguageLevelForFile(@NotNull PsiFile file) {
    PsiFile originalPythonFile = file.getOriginalFile();
    if (originalPythonFile != file) {
      // myOriginalFile could be an instance of base language
      // see PostfixLiveTemplate#copyFile
      if (originalPythonFile.getViewProvider() instanceof TemplateLanguageFileViewProvider) {
        originalPythonFile = originalPythonFile.getViewProvider().getPsi(PythonLanguage.getInstance());
      }
      if (originalPythonFile instanceof PyFile) {
        return ((PyFile)originalPythonFile).getLanguageLevel();
      }
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      virtualFile = file.getViewProvider().getVirtualFile();
    }
    return getLanguageLevelForVirtualFile(file.getProject(), virtualFile);
  }

  /**
   * Returns Python language level for a virtual file.
   *
   * @see LanguageLevel#forElement
   */
  public static @NotNull LanguageLevel getLanguageLevelForVirtualFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    if (virtualFile instanceof VirtualFileWindow) {
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
    }
    virtualFile = BackedVirtualFile.getOriginFileIfBacked(virtualFile);

    final LanguageLevel forced = LanguageLevel.FORCE_LANGUAGE_LEVEL;
    if (ApplicationManager.getApplication().isUnitTestMode() && forced != null) return forced;

    final LanguageLevel specified = specifiedFileLanguageLevel(virtualFile);
    if (specified != null) return specified;

    final Sdk sdk = virtualFile instanceof LightVirtualFile ? null : getFileSdk(project, virtualFile);
    if (sdk != null) return PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk);

    return guessLanguageLevelWithCaching(project, () -> getPythonModuleSdks(project).values());
  }

  private final class UpdateRootTask implements Runnable {
    private final @NotNull Project myProject;
    private final @NotNull VirtualFile myRoot;
    private final @NotNull LanguageLevel myLanguageLevel;
    private final boolean myShouldSuppressSizeLimit;
    private final boolean myIterateAsContent;

    UpdateRootTask(@NotNull Project project, @NotNull VirtualFile root, @NotNull LanguageLevel languageLevel,
                   boolean shouldSuppressSizeLimit, boolean iterateAsContent) {
      myProject = project;
      myRoot = root;
      myLanguageLevel = languageLevel;
      myShouldSuppressSizeLimit = shouldSuppressSizeLimit;
      myIterateAsContent = iterateAsContent;
    }

    @Override
    public void run() {
      // This code is a copy-pasted behaviour of ModuleIndexableFilesIteratorImpl and SdkIndexableFilesIteratorImpl.
      // Since they are planned to become obsolete next release, and API to iterate any indexable directory/file would be provided,
      // this dirty solution is good enough
      if (myIterateAsContent) {
        ModuleFileIndex index = ReadAction.compute(() -> {
          if (myProject.isDisposed() || !myRoot.isValid()) return null;
          Module module = ProjectFileIndex.getInstance(myProject).getModuleForFile(myRoot, false);
          if (module == null) return null;
          return ModuleRootManager.getInstance(module).getFileIndex();
        });
        if (index == null) return;

        final PushedFilePropertiesUpdater propertiesUpdater = PushedFilePropertiesUpdater.getInstance(myProject);
        index.iterateContentUnderDirectory(myRoot, (ContentIteratorEx)file -> {
          if (visitFileToPush(file, propertiesUpdater)) {
            return TreeNodeProcessingResult.CONTINUE;
          }
          return TreeNodeProcessingResult.SKIP_CHILDREN;
        });
        return;
      }

      if (myProject.isDisposed() || !ReadAction.compute(() -> myRoot.isValid())) return;
      final PushedFilePropertiesUpdater propertiesUpdater = PushedFilePropertiesUpdater.getInstance(myProject);

      ProjectFileIndex projectFileIndex = ReadAction.compute(() -> {
        if (myProject.isDisposed()) return null;
        return ProjectFileIndex.getInstance(myProject);
      });
      if (projectFileIndex == null) return;
      VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (!shouldVisit(file, projectFileIndex, myRoot)) return false;
          return visitFileToPush(file, propertiesUpdater);
        }
      });
    }

    private static boolean shouldVisit(@NotNull VirtualFile file, @NotNull ProjectFileIndex index, @NotNull VirtualFile root) {
      if (file.is(VFileProperty.SYMLINK)) {
        if (!Registry.is("indexer.follows.symlinks")) {
          return false;
        }
        VirtualFile targetFile = file.getCanonicalFile();
        if (targetFile == null || targetFile.is(VFileProperty.SYMLINK)) {
          // Broken or recursive symlink. The second check should not happen but let's guarantee no StackOverflowError.
          return false;
        }
        if (root.equals(file)) {
          return true;
        }
        return shouldVisit(targetFile, index, root);
      }
      if (!(file instanceof VirtualFileWithId) || ((VirtualFileWithId)file).getId() <= 0) {
        return false;
      }
      if (ReadAction.compute(() -> index.isExcluded(file))) {
        return false;
      }
      return true;
    }

    private Boolean visitFileToPush(@NotNull VirtualFile file, PushedFilePropertiesUpdater propertiesUpdater) {
      return ReadAction.compute(() -> {
        if (!file.isValid() || PyModuleService.getInstance().isFileIgnored(file)) {
          return false;
        }
        if (file.isDirectory()) {
          propertiesUpdater.findAndUpdateValue(
            file,
            PythonLanguageLevelPusher.this,
            myLanguageLevel
          );
        }
        if (myShouldSuppressSizeLimit) {
          SingleRootFileViewProvider.doNotCheckFileSizeLimit(file);
        }
        return true;
      });
    }

    @Override
    public String toString() {
      return "UpdateRootTask{" +
             "myRoot=" + myRoot +
             ", myLanguageLevel=" + myLanguageLevel +
             '}';
    }
  }

  @TestOnly
  public static void setForcedLanguageLevel(@NotNull Project project, @Nullable LanguageLevel languageLevel) {
    LanguageLevel.FORCE_LANGUAGE_LEVEL = languageLevel;
    PushedFilePropertiesUpdater.getInstance(project).pushAll(new PythonLanguageLevelPusher());
  }

  public static void specifyFileLanguageLevel(@NotNull VirtualFile file, @Nullable LanguageLevel languageLevel) {
    KEY.setPersistentValue(file, languageLevel);
  }

  private static @Nullable LanguageLevel specifiedFileLanguageLevel(@Nullable VirtualFile file) {
    if (file == null) return null;

    final LanguageLevel specified = KEY.getPersistentValue(file);
    if (file.isDirectory()) {
      // no need to check parent since UpdateRootTask pushes language level into all directories under roots
      return specified;
    }
    else {
      return specified == null ? specifiedFileLanguageLevel(file.getParent()) : specified;
    }
  }

  @TestOnly
  public void flushLanguageLevelCache() {
    myModuleSdks.clear();
  }

  private final class MyDumbModeTask extends DumbModeTask {
    private final @NotNull Project project;
    private final @NotNull Map<Module, Sdk> moduleSdks;
    private final SimpleMessageBusConnection connection;

    private MyDumbModeTask(@NotNull Project project, @NotNull Map<Module, Sdk> moduleSdks) {
      this.project = project;
      this.moduleSdks = moduleSdks;
      connection = project.getMessageBus().simpleConnect();
      connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
        @Override
        public void rootsChanged(@NotNull ModuleRootEvent event) {
          DumbService.getInstance(project).cancelTask(MyDumbModeTask.this);
        }
      });
    }

    @Override
    public void dispose() {
      connection.disconnect();
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      if (project.isDisposed()) {
        return;
      }

      //final PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
      indicator.setIndeterminate(true);
      indicator.setText(IndexingBundle.message("progress.indexing.scanning"));
      final List<Runnable> tasks = ReadAction.compute(() -> getRootUpdateTasks(project, moduleSdks));
      PushedFilePropertiesUpdater.getInstance(project).runConcurrentlyIfPossible(tasks);
      //if (!ApplicationManager.getApplication().isUnitTestMode()) {
      //  snapshot.logResponsivenessSinceCreation("Pushing Python language level to " + tasks.size() + " roots in " + sdks.size() +
      //                                          " SDKs");
      //}
    }

    @Override
    public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof MyDumbModeTask &&
          ((MyDumbModeTask)taskFromQueue).project.equals(project) &&
          ((MyDumbModeTask)taskFromQueue).moduleSdks.equals(moduleSdks)) {
        return this;
      }
      return null;
    }
  }
}
