// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Maps;
import com.intellij.ProjectTopics;
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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.module.PyModuleService;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.sdk.PythonSdkUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class PythonLanguageLevelPusher implements FilePropertyPusher<String> {
  private static final Key<String> KEY = new Key<>("python.language.level");
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

  private final Map<Module, Sdk> myModuleSdks = ContainerUtil.createWeakMap();

  @Override
  public void initExtra(@NotNull Project project) {
    final Map<Module, Sdk> moduleSdks = getPythonModuleSdks(project);
    final Set<Sdk> distinctSdks = StreamEx.ofValues(moduleSdks).nonNull().collect(Collectors.toCollection(LinkedHashSet::new));

    myModuleSdks.putAll(moduleSdks);
    resetProjectLanguageLevel(project);
    updateSdkLanguageLevels(project, distinctSdks);
    guessLanguageLevelWithCaching(project);
  }

  @Override
  @NotNull
  public Key<String> getFileDataKey() {
    return KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return true;
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return LanguageLevel.toPythonVersion(LanguageLevel.getDefault());
  }

  @Override
  @Nullable
  public String getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return null;
  }

  @Nullable
  private static Sdk getFileSdk(@NotNull Project project, @NotNull VirtualFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null) {
      final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk != null) {
        return sdk;
      }
      return null;
    }
    else {
      return findSdkForFileOutsideTheProject(project, file);
    }
  }

  @Nullable
  private static Sdk findSdkForFileOutsideTheProject(Project project, VirtualFile file) {
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
  @NotNull
  public String getImmediateValue(@NotNull Module module) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.toPythonVersion(LanguageLevel.FORCE_LANGUAGE_LEVEL);
    }

    final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    return LanguageLevel.toPythonVersion(PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk));
  }

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file, @NotNull Project project) {
    return false;
  }

  @Override
  public boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    return true;
  }

  private static final FileAttribute PERSISTENCE = new FileAttribute("python_language_level_persistence", 2, true);

  private static boolean areLanguageLevelsCompatible(LanguageLevel oldLevel, LanguageLevel newLevel) {
    return oldLevel != null && newLevel != null && COMPATIBLE_LEVELS.get(oldLevel) == newLevel;
  }

  @Override
  public void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull String value) throws IOException {
    final LanguageLevel level = LanguageLevel.fromPythonVersion(value);
    final DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir);

    LanguageLevel oldLanguageLevel = null;
    if (iStream != null) {
      try {
        final int oldLevelOrdinal = DataInputOutputUtil.readINT(iStream);
        if (oldLevelOrdinal == level.ordinal()) return;
        oldLanguageLevel = Arrays.stream(LanguageLevel.values()).filter(it -> it.ordinal() == oldLevelOrdinal).findFirst().orElse(null);
      }
      finally {
        iStream.close();
      }
    }

    try (DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir)) {
      DataInputOutputUtil.writeINT(oStream, level.ordinal());
    }

    if (!areLanguageLevelsCompatible(oldLanguageLevel, level) || !ProjectFileIndex.getInstance(project).isInContent(fileOrDir)) {
      PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, PythonLanguageLevelPusher::isPythonFile);
    }
    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && isPythonFile(child)) {
        clearSdkPathCache(child);
      }
    }
  }

  private static boolean isPythonFile(VirtualFile child) {
    return PythonFileType.INSTANCE.equals(FileTypeRegistry.getInstance().getFileTypeByFileName(child.getNameSequence()));
  }

  private static void clearSdkPathCache(@NotNull final VirtualFile child) {
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
  public void afterRootsChanged(@NotNull final Project project) {
    final Map<Module, Sdk> moduleSdks = getPythonModuleSdks(project);
    final Set<Sdk> distinctSdks = StreamEx.ofValues(moduleSdks).nonNull().collect(Collectors.toCollection(LinkedHashSet::new));
    final boolean needToReparseOpenFiles = StreamEx.of(moduleSdks.entrySet()).anyMatch((entry -> {
      final Module module = entry.getKey();
      final Sdk newSdk = entry.getValue();
      final Sdk oldSdk = myModuleSdks.get(module);
      return myModuleSdks.containsKey(module) && newSdk != oldSdk;
    }));

    myModuleSdks.putAll(moduleSdks);
    resetProjectLanguageLevel(project);
    updateSdkLanguageLevels(project, distinctSdks);
    guessLanguageLevelWithCaching(project);

    if (needToReparseOpenFiles) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed()) {
          return;
        }
        PythonCodeStyleService.getInstance().reparseOpenEditorFiles(project);
      });
    }
  }

  @NotNull
  private static Map<Module, Sdk> getPythonModuleSdks(@NotNull Project project) {
    final Map<Module, Sdk> result = new LinkedHashMap<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (isPythonModule(module)) {
        result.put(module, PythonSdkUtil.findPythonSdk(module));
      }
    }
    return result;
  }

  private static boolean isPythonModule(@NotNull final Module module) {
    return PyModuleService.getInstance().isPythonModule(module);
  }

  private void updateSdkLanguageLevels(@NotNull Project project, @NotNull Set<Sdk> sdks) {
    if (sdks.isEmpty()) {
      return;
    }

    DumbService.getInstance(project).queueTask(new MyDumbModeTask(project, sdks));
  }

  private List<Runnable> getRootUpdateTasks(@NotNull Project project, @NotNull Set<Sdk> sdks) {
    final List<Runnable> results = new ArrayList<>();
    for (Sdk sdk : sdks) {
      final LanguageLevel languageLevel = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk);
      for (VirtualFile root : sdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
        if (!root.isValid() || PyTypeShed.INSTANCE.isInside(root)) {
          continue;
        }
        final VirtualFile parent = root.getParent();
        final boolean shouldSuppressSizeLimit = parent != null && parent.getName().equals(PythonSdkUtil.SKELETON_DIR_NAME);
        results.add(new UpdateRootTask(project, root, languageLevel, shouldSuppressSizeLimit));
      }
    }
    return results;
  }

  @NotNull
  private static LanguageLevel guessLanguageLevelWithCaching(@NotNull Project project) {
    LanguageLevel languageLevel = LanguageLevel.fromPythonVersion(project.getUserData(KEY));
    if (languageLevel == null) {
      languageLevel = guessLanguageLevel(project);
      project.putUserData(KEY, LanguageLevel.toPythonVersion(languageLevel));
    }

    return languageLevel;
  }

  private static void resetProjectLanguageLevel(@NotNull Project project) {
    project.putUserData(KEY, null);
  }

  @NotNull
  private static LanguageLevel guessLanguageLevel(@NotNull Project project) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    if (moduleManager != null) {
      LanguageLevel maxLevel = null;
      for (Module projectModule : moduleManager.getModules()) {
        final Sdk sdk = PythonSdkUtil.findPythonSdk(projectModule);
        if (sdk != null) {
          final LanguageLevel level = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk);
          if (maxLevel == null || maxLevel.isOlderThan(level)) {
            maxLevel = level;
          }
        }
      }
      if (maxLevel != null) {
        return maxLevel;
      }
    }
    return LanguageLevel.getDefault();
  }

  /**
   * Returns Python language level for a virtual file.
   *
   * @see LanguageLevel#forElement
   */
  @NotNull
  public static LanguageLevel getLanguageLevelForVirtualFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
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

    return guessLanguageLevelWithCaching(project);
  }

  private final class UpdateRootTask implements Runnable {
    @NotNull private final Project myProject;
    @NotNull private final VirtualFile myRoot;
    @NotNull private final LanguageLevel myLanguageLevel;
    private final boolean myShouldSuppressSizeLimit;

    UpdateRootTask(@NotNull Project project, @NotNull VirtualFile root, @NotNull LanguageLevel languageLevel,
                   boolean shouldSuppressSizeLimit) {
      myProject = project;
      myRoot = root;
      myLanguageLevel = languageLevel;
      myShouldSuppressSizeLimit = shouldSuppressSizeLimit;
    }

    @Override
    public void run() {
      if (myProject.isDisposed() || !ReadAction.compute(() -> myRoot.isValid())) return;


      final PushedFilePropertiesUpdater propertiesUpdater = PushedFilePropertiesUpdater.getInstance(myProject);

      VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          return ReadAction.compute(() -> {
            if (PyModuleService.getInstance().isFileIgnored(file)) {
              return false;
            }
            if (file.isDirectory()) {
              propertiesUpdater.findAndUpdateValue(
                file,
                PythonLanguageLevelPusher.this,
                LanguageLevel.toPythonVersion(myLanguageLevel)
              );
            }
            if (myShouldSuppressSizeLimit) {
              SingleRootFileViewProvider.doNotCheckFileSizeLimit(file);
            }
            return true;
          });
        }
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
    file.putUserData(KEY, LanguageLevel.toPythonVersion(languageLevel));
  }

  @Nullable
  private static LanguageLevel specifiedFileLanguageLevel(@Nullable VirtualFile file) {
    if (file == null) return null;

    final LanguageLevel specified = LanguageLevel.fromPythonVersion(file.getUserData(KEY));
    if (file.isDirectory()) {
      return specified;
    }
    else {
      return specified == null ? specifiedFileLanguageLevel(file.getParent()) : specified;
    }
  }

  public void flushLanguageLevelCache() {
    myModuleSdks.clear();
  }

  private final class MyDumbModeTask extends DumbModeTask {
    private @NotNull final Project project;
    private @NotNull final Set<Sdk> sdks;
    private final SimpleMessageBusConnection connection;

    private MyDumbModeTask(@NotNull Project project, @NotNull Set<Sdk> sdks) {
      this.project = project;
      this.sdks = sdks;
      connection = project.getMessageBus().simpleConnect();
      connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
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
      final List<Runnable> tasks = ReadAction.compute(() -> getRootUpdateTasks(project, sdks));
      PushedFilePropertiesUpdater.getInstance(project).runConcurrentlyIfPossible(tasks);
      //if (!ApplicationManager.getApplication().isUnitTestMode()) {
      //  snapshot.logResponsivenessSinceCreation("Pushing Python language level to " + tasks.size() + " roots in " + sdks.size() +
      //                                          " SDKs");
      //}
    }
  }
}
