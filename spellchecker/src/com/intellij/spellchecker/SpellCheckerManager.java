// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.google.common.collect.Maps;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.*;
import com.intellij.spellchecker.engine.SpellCheckerEngine;
import com.intellij.spellchecker.engine.SuggestionProvider;
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine;
import com.intellij.spellchecker.grazie.GrazieSuggestionProvider;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.spellchecker.state.CachedDictionaryState;
import com.intellij.spellchecker.state.DictionaryStateListener;
import com.intellij.spellchecker.state.ProjectDictionaryState;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.*;

import static com.intellij.openapi.application.PathManager.getOptionsPath;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.visitChildrenRecursively;
import static com.intellij.project.ProjectKt.getProjectStoreDirectory;

public class SpellCheckerManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(SpellCheckerManager.class);

  private static final int MAX_METRICS = 1;
  public static final String PROJECT = "project";
  public static final String APP = "application";
  private final Project project;
  private ProjectDictionary myProjectDictionary;
  private EditableDictionary myAppDictionary;
  private final SpellCheckerSettings settings;
  private final VirtualFileListener myCustomDictFileListener;
  private final String myProjectDictionaryPath;
  private final String myAppDictionaryPath;
  public static final String PROJECT_DICTIONARY_PATH =
    "dictionaries" + File.separator + System.getProperty("user.name").replace('.', '_') + ".xml";
  public static final String CACHED_DICTIONARY_FILE = "cachedDictionary.xml";

  private final EventDispatcher<DictionaryStateListener> myUserDictionaryListenerEventDispatcher =
    EventDispatcher.create(DictionaryStateListener.class);

  private SpellCheckerEngine mySpellChecker;
  private SuggestionProvider mySuggestionProvider;


  public static SpellCheckerManager getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerManager.class);
  }

  public SpellCheckerManager(Project project) {
    this.project = project;
    this.settings = SpellCheckerSettings.getInstance(project);

    this.mySpellChecker = new GrazieSpellCheckerEngine(project);
    this.mySuggestionProvider = new GrazieSuggestionProvider(mySpellChecker);

    fullConfigurationReload();

    final VirtualFile projectStoreDir = project.getBaseDir() != null ? getProjectStoreDirectory(project.getBaseDir()) : null;
    myProjectDictionaryPath = projectStoreDir != null ? projectStoreDir.getPath() + File.separator + PROJECT_DICTIONARY_PATH : "";
    myAppDictionaryPath = getOptionsPath() + File.separator + CACHED_DICTIONARY_FILE;
    myCustomDictFileListener = new CustomDictFileListener(settings);
    LocalFileSystem.getInstance().addVirtualFileListener(myCustomDictFileListener);
    BundledDictionaryProvider.EP_NAME.addChangeListener(this::fillEngineDictionary, this);
    RuntimeDictionaryProvider.EP_NAME.addChangeListener(this::fillEngineDictionary, this);
    CustomDictionaryProvider.EP_NAME.addChangeListener(this::fillEngineDictionary, this);
  }

  @SuppressWarnings("unused")  // used in Rider
  public SpellCheckerEngine getSpellChecker() {
    return mySpellChecker;
  }

  public void fullConfigurationReload() {
    mySpellChecker = new GrazieSpellCheckerEngine(project);
    mySuggestionProvider = new GrazieSuggestionProvider(mySpellChecker);
    fillEngineDictionary();
  }

  public void updateBundledDictionaries(final List<String> removedDictionaries) {
    for (BundledDictionaryProvider provider : BundledDictionaryProvider.EP_NAME.getExtensionList()) {
      for (String dictionary : provider.getBundledDictionaries()) {
        if (!mySpellChecker.isDictionaryLoad(dictionary)) {
          loadBundledDictionary(provider, dictionary);
        }
      }
    }

    for (RuntimeDictionaryProvider provider : RuntimeDictionaryProvider.EP_NAME.getExtensionList()) {
      for (Dictionary dictionary : provider.getDictionaries()) {
        boolean dictionaryShouldBeLoad = settings == null || !settings.getRuntimeDisabledDictionariesNames().contains(dictionary.getName());
        boolean dictionaryIsLoad = mySpellChecker.isDictionaryLoad(dictionary.getName());
        if (dictionaryIsLoad && !dictionaryShouldBeLoad) {
          mySpellChecker.removeDictionary(dictionary.getName());
        }
        else if (!dictionaryIsLoad && dictionaryShouldBeLoad) {
          loadRuntimeDictionary(dictionary);
        }
      }
    }

    if (settings != null && settings.getCustomDictionariesPaths() != null) {
      for (String dictionary : settings.getCustomDictionariesPaths()) {
        if (!mySpellChecker.isDictionaryLoad(dictionary)) {
          loadDictionary(dictionary);
        }
      }
    }

    if (!ContainerUtil.isEmpty(removedDictionaries)) {
      for (String name : removedDictionaries) {
        mySpellChecker.removeDictionary(name);
      }
    }

    restartInspections();
  }

  public Project getProject() {
    return project;
  }

  @NotNull
  public Set<String> getUserDictionaryWords() {
    return ContainerUtil.union(myProjectDictionary.getEditableWords(), myAppDictionary.getEditableWords());
  }

  private void fillEngineDictionary() {
    mySpellChecker.reset();

    loadBundledDictionaries();
    loadRuntimeDictionaries();
    loadCustomDictionaries();

    // Load custom dictionaries
    initUserDictionaries();
  }

  private void loadBundledDictionaries() {
    for (BundledDictionaryProvider provider : BundledDictionaryProvider.EP_NAME.getExtensionList()) {
      for (String dictionary : provider.getBundledDictionaries()) {
        loadBundledDictionary(provider, dictionary);
      }
    }
  }

  private void loadRuntimeDictionaries() {
    for (RuntimeDictionaryProvider provider : RuntimeDictionaryProvider.EP_NAME.getExtensionList()) {
      for (Dictionary dictionary : provider.getDictionaries()) {
        if (settings == null || !settings.getRuntimeDisabledDictionariesNames().contains(dictionary.getName())) {
          loadRuntimeDictionary(dictionary);
        }
      }
    }
  }

  private void loadCustomDictionaries() {
    if (settings != null && settings.getCustomDictionariesPaths() != null) {
      settings.getCustomDictionariesPaths().forEach(this::loadDictionary);
    }
  }

  private void initUserDictionaries() {
    CachedDictionaryState cachedDictionaryState = CachedDictionaryState.getInstance();
    cachedDictionaryState.addCachedDictListener((dict) -> restartInspections());
    if (cachedDictionaryState.getDictionary() == null) {
      cachedDictionaryState.setDictionary(new UserDictionary(CachedDictionaryState.DEFAULT_NAME));
    }
    myAppDictionary = cachedDictionaryState.getDictionary();
    mySpellChecker.addModifiableDictionary(myAppDictionary);

    final ProjectDictionaryState dictionaryState = ServiceManager.getService(project, ProjectDictionaryState.class);
    dictionaryState.addProjectDictListener((dict) -> restartInspections());
    myProjectDictionary = dictionaryState.getProjectDictionary();
    myProjectDictionary.setActiveName(System.getProperty("user.name"));
    mySpellChecker.addModifiableDictionary(myProjectDictionary);
  }

  private void loadDictionary(@NotNull String path) {
    CustomDictionaryProvider dictionaryProvider = findApplicable(path);
    if (dictionaryProvider != null) {
      final Dictionary dictionary = dictionaryProvider.get(path);
      if (dictionary != null) {
        mySpellChecker.addDictionary(dictionary);
      }
    }
    else {
      mySpellChecker.loadDictionary(new FileLoader(path));
    }
  }

  private void loadBundledDictionary(@NotNull BundledDictionaryProvider provider, @NotNull String dictionary) {
    Class<? extends BundledDictionaryProvider> loaderClass = provider.getClass();
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") //closed in StreamLoader
      InputStream stream = loaderClass.getResourceAsStream(dictionary);
    if (stream != null) {
      mySpellChecker.loadDictionary(new StreamLoader(stream, dictionary));
    }
    else {
      LOG.warn("Couldn't load dictionary '" + dictionary + "' with loader '" + loaderClass + "'");
    }
  }

  private void loadRuntimeDictionary(@NotNull Dictionary dictionary) {
    mySpellChecker.addDictionary(dictionary);
  }

  public boolean hasProblem(@NotNull String word) {
    return !mySpellChecker.isCorrect(word);
  }

  public void acceptWordAsCorrect(@NotNull String word, Project project) {
    acceptWordAsCorrect(word, null, project, DictionaryLevel.PROJECT); // TODO: or default
  }

  public void acceptWordAsCorrect(@NotNull String word,
                                  @Nullable VirtualFile file,
                                  @NotNull Project project,
                                  @NotNull DictionaryLevel dictionaryLevel) {
    if (DictionaryLevel.NOT_SPECIFIED == dictionaryLevel) return;

    final String transformed = mySpellChecker.getTransformation().transform(word);
    final EditableDictionary dictionary = DictionaryLevel.PROJECT == dictionaryLevel ? myProjectDictionary : myAppDictionary;
    if (transformed != null) {
      if (file != null) {
        WriteCommandAction.writeCommandAction(project)
          .run(() -> UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(file) {
            @Override
            public void undo() {
              removeWordFromDictionary(dictionary, transformed);
            }

            @Override
            public void redo() {
              addWordToDictionary(dictionary, transformed);
            }
          }));
      }
      addWordToDictionary(dictionary, transformed);
    }
  }

  private void addWordToDictionary(@NotNull EditableDictionary dictionary, @NotNull String word) {
    dictionary.addToDictionary(word);
    fireDictionaryChanged(dictionary);
  }

  private void removeWordFromDictionary(@NotNull EditableDictionary dictionary, String transformed) {
    dictionary.removeFromDictionary(transformed);
    fireDictionaryChanged(dictionary);
  }

  private void fireDictionaryChanged(@NotNull EditableDictionary dictionary) {
    myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(dictionary);
    restartInspections();
    SaveAndSyncHandler.getInstance().scheduleProjectSave(project);
  }

  public void updateUserDictionary(@NotNull Collection<String> words) {
    // new for project dictionary
    Collection<String> addedToProjectWords = ContainerUtil.subtract(words, getUserDictionaryWords());
    addedToProjectWords.forEach(myProjectDictionary::addToDictionary);

    // deleted from project dictionary
    Collection<String> deletedFromProjectWords = ContainerUtil.subtract(myProjectDictionary.getEditableWords(), words);
    deletedFromProjectWords.forEach(myProjectDictionary::removeFromDictionary);

    if (addedToProjectWords.size() + deletedFromProjectWords.size() > 0) {
      myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(myProjectDictionary);
    }

    // deleted from application dictionary
    Collection<String> deletedFromApplicationWords = ContainerUtil.subtract(myAppDictionary.getEditableWords(), words);
    deletedFromApplicationWords.forEach(myAppDictionary::removeFromDictionary);

    if (deletedFromApplicationWords.size() > 0) {
      myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(myAppDictionary);
    }

    restartInspections();
  }

  @NotNull
  public static List<String> getBundledDictionaries() {
    final ArrayList<String> dictionaries = new ArrayList<>();
    for (BundledDictionaryProvider provider : BundledDictionaryProvider.EP_NAME.getExtensionList()) {
      ContainerUtil.addAll(dictionaries, provider.getBundledDictionaries());
    }
    return dictionaries;
  }

  @NotNull
  public static List<Dictionary> getRuntimeDictionaries() {
    final ArrayList<Dictionary> dictionaries = new ArrayList<>();
    for (RuntimeDictionaryProvider provider : RuntimeDictionaryProvider.EP_NAME.getExtensionList()) {
      ContainerUtil.addAll(dictionaries, provider.getDictionaries());
    }
    return dictionaries;
  }

  @NotNull
  public List<String> getSuggestions(@NotNull String text) {
    final int correctionsLimit = Registry.intValue("spellchecker.corrections.limit", 5);
    return mySuggestionProvider.getSuggestions(text, correctionsLimit, MAX_METRICS);
  }

  public static void restartInspections() {
    ApplicationManager.getApplication().invokeLater(() -> {
      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (Project project1 : projects) {
        if (project1.isInitialized() && project1.isOpen() && !project1.isDefault()) {
          DaemonCodeAnalyzer.getInstance(project1).restart();
        }
      }
    });
  }

  @Nullable
  private static CustomDictionaryProvider findApplicable(@NotNull String path) {
    return CustomDictionaryProvider.EP_NAME.getExtensionList().stream()
      .filter(dictionaryProvider -> dictionaryProvider.isApplicable(path))
      .findAny()
      .orElse(null);
  }

  @Override
  public void dispose() {
    LocalFileSystem.getInstance().removeVirtualFileListener(myCustomDictFileListener);
  }

  @NotNull
  public String getProjectDictionaryPath() {
    return myProjectDictionaryPath;
  }

  @NotNull
  public String getAppDictionaryPath() {
    return myAppDictionaryPath;
  }

  public void openDictionaryInEditor(@NotNull String dictPath) {
    final VirtualFile file = StringUtil.isEmpty(dictPath) ? null : LocalFileSystem
      .getInstance().refreshAndFindFileByPath(dictPath);
    if (file == null) {
      final String title = SpellCheckerBundle.message("dictionary.not.found.title");
      final String message = SpellCheckerBundle.message("dictionary.not.found", dictPath);
      Messages.showMessageDialog(project, message, title, Messages.getWarningIcon());
      return;
    }

    final FileEditorManager fileManager = FileEditorManager.getInstance(project);
    if (fileManager != null) {
      fileManager.openFile(file, true);
    }
  }

  @SuppressWarnings("unused")  // used in Rider
  public void addUserDictionaryChangedListener(DictionaryStateListener listener, Disposable parentDisposable) {
    myUserDictionaryListenerEventDispatcher.addListener(listener);
    Disposer.register(parentDisposable, () -> myUserDictionaryListenerEventDispatcher.removeListener(listener));
  }

  public enum DictionaryLevel {
    APP("application-level"), PROJECT("project-level"), NOT_SPECIFIED("not specified");
    private final String myName;

    @SuppressWarnings("ConstantConditions")
    private final static Map<String, DictionaryLevel> DICTIONARY_LEVELS =
      Maps.uniqueIndex(EnumSet.allOf(DictionaryLevel.class), DictionaryLevel::getName);

    DictionaryLevel(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }

    @NotNull
    public static DictionaryLevel getLevelByName(@NotNull String name) {
      return DICTIONARY_LEVELS.getOrDefault(name, NOT_SPECIFIED);
    }
  }

  private class CustomDictFileListener implements VirtualFileListener {
    private final SpellCheckerSettings mySettings;

    CustomDictFileListener(@NotNull SpellCheckerSettings settings) {mySettings = settings;}

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      removeCustomDictionaries(event.getFile().getPath());
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      loadCustomDictionaries(event.getFile());
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
      final String oldPath = event.getOldParent().getPath() + File.separator + event.getFileName();
      if (!affectCustomDicts(oldPath)) {
        loadCustomDictionaries(event.getFile());
      }
      else {
        final String newPath = event.getNewParent().getPath() + File.separator + event.getFileName();
        if (!affectCustomDicts(newPath)) {
          removeCustomDictionaries(oldPath);
        }
      }
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
      final String path = toSystemDependentName(event.getFile().getPath());

      if (!mySpellChecker.isDictionaryLoad(path)) return;

      mySpellChecker.removeDictionary(path);
      loadDictionary(path);
      restartInspections();
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
      final VirtualFile file = event.getFile();
      if (file.isDirectory()) return;

      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        final String oldName = (String)event.getOldValue();
        if (!isDic(oldName)) {
          loadCustomDictionaries(file);
        }
        else {
          final String newName = (String)event.getNewValue();
          if (!isDic(newName)) {
            removeCustomDictionaries(file.getParent().getPath() + File.separator + oldName);
          }
        }
      }
    }

    private void removeCustomDictionaries(@NotNull String path) {
      final String systemDependentPath = toSystemDependentName(path);
      if (affectCustomDicts(path)) {
        mySpellChecker.removeDictionariesRecursively(systemDependentPath);
        mySettings.getCustomDictionariesPaths().removeIf(dict -> isAncestor(systemDependentPath, dict, false));
        restartInspections();
      }
    }

    private void loadCustomDictionaries(@NotNull VirtualFile file) {
      final String path = toSystemDependentName(file.getPath());
      if (!affectCustomDicts(path)) return;

      visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          final boolean isDirectory = file.isDirectory();
          final String path = file.getPath();
          if (!isDirectory && mySettings.getCustomDictionariesPaths().contains(path)) {
            loadDictionary(path);
            restartInspections();
          }
          return isDirectory;
        }
      });
    }

    private boolean isDic(String path) {
      return extensionEquals(path, "dic");
    }

    private boolean affectCustomDicts(@NotNull String path) {
      return mySettings.getCustomDictionariesPaths().stream().anyMatch(dicPath -> isAncestor(path, dicPath, false));
    }
  }
}
