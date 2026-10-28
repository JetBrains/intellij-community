// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Maps;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIteratorEx;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.FilePropertyKey;
import com.intellij.psi.FilePropertyKeyImpl;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModelKt;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.backend.evolution.EvoPyProject;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeNodeProcessingResult;
import com.intellij.util.gist.GistManager;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class PythonLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {
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

  /**
   * The layout that the pusher pushed last in a project, see {@link #push}. It is kept on the project, as the pusher is an
   * application extension, and a layout holds the modules of its project.
   */
  private static final Key<PythonLanguageLevelLayout> PUSHED_LAYOUT = Key.create("python.language.level.pushed.layout");

  private static @NotNull PythonLanguageLevelLayout pushedLayout(@NotNull Project project) {
    final PythonLanguageLevelLayout layout = project.getUserData(PUSHED_LAYOUT);
    return layout != null ? layout : PythonLanguageLevelLayout.EMPTY;
  }

  @Override
  public void initExtra(@NotNull Project project) {
    final PythonLanguageLevels levels = PythonLanguageLevels.getInstance(project);
    push(project, levels.refresh(), false);
    levels.follow();
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

  /**
   * The level of [file] from the interpreter that holds it: the interpreter of its module, or an interpreter whose class
   * root holds a file outside the modules. {@code null} when no interpreter holds it.
   */
  private static @Nullable LanguageLevel getFileInterpreterLevel(@NotNull Project project,
                                                                 @NotNull PythonLanguageLevelLayout layout,
                                                                 @NotNull VirtualFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    return module != null ? layout.getModuleLevels().get(module) : layout.levelOfInterpreterFile(file);
  }

  @Override
  public @NotNull LanguageLevel getImmediateValue(@NotNull Module module) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.FORCE_LANGUAGE_LEVEL;
    }

    final LanguageLevel level = PythonLanguageLevels.layoutOf(module.getProject()).getModuleLevels().get(module);
    return level != null ? level : LanguageLevel.getDefault();
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

    VirtualFilePyLanguageLevelListener publisher = project.getMessageBus().syncPublisher(VirtualFilePyLanguageLevelListener.TOPIC);
    publisher.levelChanged(fileOrDir, level);

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && isPythonFile(child)) {
        publisher.levelChanged(child, level);
      }
    }
  }

  private static boolean isPythonFile(VirtualFile child) {
    return PythonFileType.INSTANCE.equals(FileTypeRegistry.getInstance().getFileTypeByFileName(child.getNameSequence()));
  }

  private static void clearSdkPathCache(final @NotNull VirtualFile child) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final Module module = ModuleUtilCore.findModuleForFile(child, project);
      final PythonInterpreter interpreter = module == null ? null : findModuleInterpreter(module);
      if (interpreter != null) {
        PythonSdkPathCache.getInstance(project, interpreter).clearCache();
      }
    }
  }

  @Override
  public void afterRootsChanged(final @NotNull Project project) {
    // A roots change can add a root that has no level yet, so the pusher pushes the layout even when it is the same.
    push(project, PythonLanguageLevels.layoutOf(project), true);
  }

  /**
   * Pushes [layout] when it differs from the layout it pushed last. For a change that no roots change reports: a new
   * snapshot, or a new version that the interpreter store saves. See {@link PythonLanguageLevels#follow}.
   */
  static void layoutChanged(@NotNull Project project, @NotNull PythonLanguageLevelLayout layout) {
    if (project.isDisposed()) return;
    for (FilePropertyPusher<?> pusher : EP_NAME.getExtensionList()) {
      if (pusher instanceof PythonLanguageLevelPusher languageLevelPusher) {
        languageLevelPusher.push(project, layout, false);
      }
    }
  }

  /**
   * Queues a task that pushes the levels of [layout] to the roots. Reparses the open files when the level of a module
   * changed. With [always], it also pushes a layout that is equal to the pushed one.
   */
  private void push(@NotNull Project project, @NotNull PythonLanguageLevelLayout layout, boolean always) {
    final PythonLanguageLevelLayout previous = pushedLayout(project);
    project.putUserData(PUSHED_LAYOUT, layout);
    if (!always && layout.equals(previous)) return;

    final boolean levelOfModuleChanged = ContainerUtil.exists(layout.getModuleLevels().entrySet(), entry -> {
      final LanguageLevel before = previous.getModuleLevels().get(entry.getKey());
      return before != null && before != entry.getValue();
    });
    if (!layout.getModuleLevels().isEmpty() || !layout.getInterpreterLevels().isEmpty()) {
      new MyDumbModeTask(project, layout).queue(project);
    }
    if (levelOfModuleChanged) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed()) {
          return;
        }
        PythonCodeStyleService.getInstance().reparseOpenEditorFiles(project);
      });
    }
  }

  /** The interpreter of [module] in the current snapshot, or {@code null}. */
  private static @Nullable PythonInterpreter findModuleInterpreter(@NotNull Module module) {
    final EvoPyProject evoPyProject = EvoPyProjectModelKt.findEvoPyProjectIfReady(module);
    return evoPyProject == null ? null : evoPyProject.getInterpreter();
  }

  private List<Runnable> getRootUpdateTasks(@NotNull Project project, @NotNull PythonLanguageLevelLayout layout) {
    final List<Runnable> results = new ArrayList<>();
    for (Map.Entry<Module, LanguageLevel> moduleLevel : layout.getModuleLevels().entrySet()) {
      final Module module = moduleLevel.getKey();
      if (module.isDisposed()) continue;

      for (VirtualFile root : PyUtil.getSourceRoots(module)) {
        addRootIndexingTask(root, results, project, moduleLevel.getValue(), true);
      }
    }
    for (Map.Entry<PythonInterpreter, LanguageLevel> interpreterLevel : layout.getInterpreterLevels().entrySet()) {
      final PythonInterpreter interpreter = interpreterLevel.getKey();
      if (PythonInterpreterExtKt.isDisposed(interpreter)) continue;

      for (VirtualFile root : PythonInterpreterExtKt.classRoots(interpreter, project)) {
        if (!root.isValid() || PyTypeShed.INSTANCE.isInside(root)) {
          continue;
        }
        addRootIndexingTask(root, results, project, interpreterLevel.getValue(), false);
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

  /**
   * @deprecated Use {@link PyLanguageFacade#getEffectiveLanguageLevel(Project, VirtualFile)}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull LanguageLevel getLanguageLevelForVirtualFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return PyLanguageFacade.getINSTANCE().getEffectiveLanguageLevel(project, virtualFile);
  }

  // Use `PyLanguageFacade.getEffectiveLanguageLevel(Project, VirtualFile)`
  public static @NotNull LanguageLevel getEffectiveLanguageLevel(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    final LanguageLevel forced = LanguageLevel.FORCE_LANGUAGE_LEVEL;
    if (ApplicationManager.getApplication().isUnitTestMode() && forced != null) return forced;

    final LanguageLevel specified = specifiedFileLanguageLevel(virtualFile);
    if (specified != null) return specified;

    final PythonLanguageLevelLayout layout = PythonLanguageLevels.layoutOf(project);
    final LanguageLevel interpreterLevel = virtualFile instanceof LightVirtualFile ? null : getFileInterpreterLevel(project, layout, virtualFile);
    if (interpreterLevel != null) return interpreterLevel;

    return layout.getProjectLevel();
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
      // Copied from `com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.applyPushersToFile`.
      // Preload children outside read action to avoid freezes.
      if (file.isDirectory()) {
        file.getChildren();
      }
      return ReadAction.compute(() -> {
        if (!file.isValid() || FileTypeManager.getInstance().isFileIgnored(file)) {
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

  private final class MyDumbModeTask extends DumbModeTask {
    private final @NotNull Project project;
    private final @NotNull PythonLanguageLevelLayout layout;
    private final SimpleMessageBusConnection connection;

    private MyDumbModeTask(@NotNull Project project, @NotNull PythonLanguageLevelLayout layout) {
      this.project = project;
      this.layout = layout;
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
      final List<Runnable> tasks = ReadAction.compute(() -> getRootUpdateTasks(project, layout));
      // The push reindexes every indexed Python file, and each reindex drops the PSI caches in a write action.
      // The token merges these drops into one write action.
      try (AccessToken ignored = GistManager.getInstance().mergeDependentCacheInvalidations()) {
        PushedFilePropertiesUpdater.getInstance(project).runConcurrentlyIfPossible(tasks);
      }
      //if (!ApplicationManager.getApplication().isUnitTestMode()) {
      //  snapshot.logResponsivenessSinceCreation("Pushing Python language level to " + tasks.size() + " roots in " + sdks.size() +
      //                                          " SDKs");
      //}
    }

    @Override
    public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof MyDumbModeTask queued && queued.project.equals(project) && queued.layout.equals(layout)) {
        return this;
      }
      return null;
    }
  }
}
