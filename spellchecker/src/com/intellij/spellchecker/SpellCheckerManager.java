// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.google.common.collect.Maps;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.spellchecker.dictionary.*;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.engine.SpellCheckerEngine;
import com.intellij.spellchecker.engine.SpellCheckerFactory;
import com.intellij.spellchecker.engine.SuggestionProvider;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.spellchecker.state.CachedDictionaryState;
import com.intellij.spellchecker.state.DictionaryStateListener;
import com.intellij.spellchecker.state.ProjectDictionaryState;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.openapi.application.PathManager.getOptionsPath;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.visitChildrenRecursively;
import static com.intellij.project.ProjectKt.getProjectStoreDirectory;

public class SpellCheckerManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.spellchecker.SpellCheckerManager");

  private static final int MAX_METRICS = 1;
  public static final String PROJECT = "project";
  public static final String APP = "application";
  private final Project project;
  private SpellCheckerEngine spellChecker;
  private ProjectDictionary myProjectDictionary;
  private EditableDictionary myAppDictionary;
  private final SuggestionProvider suggestionProvider = new BaseSuggestionProvider(this);
  private final SpellCheckerSettings settings;
  private final VirtualFileListener myCustomDictFileListener;
  private final String myProjectDictinaryPath;
  private final String myAppDictionaryPath;
  public static final String PROJECT_DICTIONARY_PATH =
    "dictionaries" + File.separator + System.getProperty("user.name").replace('.', '_') + ".xml";
  public static final String CACHED_DICTIONARY_FILE = "cachedDictionary.xml";

  private final EventDispatcher<DictionaryStateListener> myUserDictionaryListenerEventDispatcher = EventDispatcher.create(DictionaryStateListener.class);

  public static SpellCheckerManager getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerManager.class);
  }

  public SpellCheckerManager(Project project, SpellCheckerSettings settings) {
    this.project = project;
    this.settings = settings;
    fullConfigurationReload();

    Disposer.register(project, this);
    final VirtualFile projectStoreDir = project.getBaseDir() != null ? getProjectStoreDirectory(project.getBaseDir()) : null;
    myProjectDictinaryPath = projectStoreDir != null ? projectStoreDir.getPath() + File.separator + PROJECT_DICTIONARY_PATH : "";
    myAppDictionaryPath = getOptionsPath() + File.separator + CACHED_DICTIONARY_FILE;
    myCustomDictFileListener = new CustomDictFileListener(settings);
    LocalFileSystem.getInstance().addVirtualFileListener(myCustomDictFileListener);
  }

  @SuppressWarnings("unused")  // used in Rider
  public SpellCheckerEngine getSpellChecker() {
    return spellChecker;
  }

  public void fullConfigurationReload() {
    spellChecker = SpellCheckerFactory.create(project);
    fillEngineDictionary();
  }

  public void updateBundledDictionaries(final List<String> removedDictionaries) {
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      for (String dictionary : provider.getBundledDictionaries()) {
        boolean dictionaryShouldBeLoad = settings == null || !settings.getBundledDisabledDictionariesPaths().contains(dictionary);
        boolean dictionaryIsLoad = spellChecker.isDictionaryLoad(dictionary);
        if (dictionaryIsLoad && !dictionaryShouldBeLoad) {
          spellChecker.removeDictionary(dictionary);
        }
        else if (!dictionaryIsLoad && dictionaryShouldBeLoad) {
          final Class<? extends BundledDictionaryProvider> loaderClass = provider.getClass();
          final InputStream stream = loaderClass.getResourceAsStream(dictionary);
          if (stream != null) {
            spellChecker.loadDictionary(new StreamLoader(stream, dictionary));
          }
          else {
            LOG.warn("Couldn't load dictionary '" + dictionary + "' with loader '" + loaderClass + "'");
          }
        }
      }
    }
    if (settings != null && settings.getCustomDictionariesPaths() != null) {
      final Set<String> disabledDictionaries = settings.getDisabledDictionariesPaths();
      for (String dictionary : settings.getCustomDictionariesPaths()) {
        boolean dictionaryShouldBeLoad =!disabledDictionaries.contains(dictionary);
        boolean dictionaryIsLoad = spellChecker.isDictionaryLoad(dictionary);
        if (dictionaryIsLoad && !dictionaryShouldBeLoad) {
          spellChecker.removeDictionary(dictionary);
        }
        else if (!dictionaryIsLoad && dictionaryShouldBeLoad) {
          loadDictionary(dictionary);
        }
      }
    }

    if (!ContainerUtil.isEmpty(removedDictionaries)) {
      for (String name : removedDictionaries) {
        spellChecker.removeDictionary(name);
      }
    }

    restartInspections();
  }

  public Project getProject() {
    return project;
  }

  @NotNull
  public Set<String> getUserDictionaryWords(){
    return ContainerUtil.union(myProjectDictionary.getEditableWords(), myAppDictionary.getEditableWords());
  }


  /**
   * @deprecated will be removed in 2018.X, use
   * {@link SpellCheckerManager#acceptWordAsCorrect(String, Project)} or
   * {@link ProjectDictionaryState#getProjectDictionary() and {@link CachedDictionaryState#getDictionary()}} instead
   */
  public EditableDictionary getUserDictionary() {
    return new AggregatedDictionary(myProjectDictionary, myAppDictionary);
  }

  private void fillEngineDictionary() {
    spellChecker.reset();
    // Load bundled dictionaries from corresponding jars
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      for (String dictionary : provider.getBundledDictionaries()) {
        if (settings == null || !settings.getBundledDisabledDictionariesPaths().contains(dictionary)) {
          final Class<? extends BundledDictionaryProvider> loaderClass = provider.getClass();
          final InputStream stream = loaderClass.getResourceAsStream(dictionary);
          if (stream != null) {
            spellChecker.loadDictionary(new StreamLoader(stream, dictionary));
          }
          else {
            LOG.warn("Couldn't load dictionary '" + dictionary + "' with loader '" + loaderClass + "'");
          }
        }
      }
    }
    if (settings != null && settings.getCustomDictionariesPaths() != null) {
      final Set<String> disabledDictionaries = settings.getDisabledDictionariesPaths();
      for (String dictionary : settings.getCustomDictionariesPaths()) {
        if (!disabledDictionaries.contains(dictionary)) {
          loadDictionary(dictionary);
        }
      }
    }
    initUserDictionaries();
  }

  private void initUserDictionaries() {
    final CachedDictionaryState cachedDictionaryState = ServiceManager.getService(project, CachedDictionaryState.class);
    cachedDictionaryState.addCachedDictListener((dict) -> restartInspections());
    if (cachedDictionaryState.getDictionary() == null) {
      cachedDictionaryState.setDictionary(new UserDictionary(CachedDictionaryState.DEFAULT_NAME));
    }
    myAppDictionary = cachedDictionaryState.getDictionary();
    spellChecker.addModifiableDictionary(myAppDictionary);
    
    final ProjectDictionaryState dictionaryState = ServiceManager.getService(project, ProjectDictionaryState.class);
    dictionaryState.addProjectDictListener((dict) -> restartInspections());
    myProjectDictionary = dictionaryState.getProjectDictionary();
    myProjectDictionary.setActiveName(System.getProperty("user.name"));
    spellChecker.addModifiableDictionary(myProjectDictionary);
  }

  private void loadDictionary(String path) {
    final CustomDictionaryProvider dictionaryProvider = findApplicable(path);
    if (dictionaryProvider != null) {
      final Dictionary dictionary = dictionaryProvider.get(path);
      if(dictionary != null) {
        spellChecker.addDictionary(dictionary);
      }
    }
    else spellChecker.loadDictionary(new FileLoader(path));
  }

  public boolean hasProblem(@NotNull String word) {
    return !spellChecker.isCorrect(word);
  }

  public void acceptWordAsCorrect(@NotNull String word, Project project) {
    acceptWordAsCorrect(word, null, project, DictionaryLevel.PROJECT); // TODO: or default
  }

  public void acceptWordAsCorrect(@NotNull String word,
                                  @Nullable VirtualFile file,
                                  @NotNull Project project,
                                  @NotNull DictionaryLevel dictionaryLevel) {
    if (DictionaryLevel.NOT_SPECIFIED == dictionaryLevel) return;

    final String transformed = spellChecker.getTransformation().transform(word);
    final EditableDictionary dictionary = DictionaryLevel.PROJECT == dictionaryLevel ? myProjectDictionary : myAppDictionary;
    if (transformed != null) {
      if(file != null) {
        UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(file) {
          @Override
          public void undo() {
            dictionary.removeFromDictionary(transformed);
            myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(dictionary);
            restartInspections();
          }

          @Override
          public void redo() {
            dictionary.addToDictionary(transformed);
            myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(dictionary);
            restartInspections();
          }
        });
      }
      dictionary.addToDictionary(transformed);
      myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(dictionary);
      restartInspections();
    }
  }

  public void updateUserDictionary(@NotNull Collection<String> words) {
    // new for project dictionary
    Collection<String> addedToProjectWords = ContainerUtil.subtract(words, getUserDictionaryWords());
    addedToProjectWords.forEach(myProjectDictionary::addToDictionary);

    // deleted from project dictionary
    Collection<String> deletedFromProjectWords = ContainerUtil.subtract(myProjectDictionary.getEditableWords(), words);
    deletedFromProjectWords.forEach(myProjectDictionary::removeFromDictionary);

    if (addedToProjectWords.size() + deletedFromProjectWords.size() > 0)
      myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(myProjectDictionary);

    // deleted from application dictionary
    Collection<String> deletedFromApplicationWords = ContainerUtil.subtract(myAppDictionary.getEditableWords(), words);
    deletedFromApplicationWords.forEach(myAppDictionary::removeFromDictionary);

    if (deletedFromApplicationWords.size() > 0)
      myUserDictionaryListenerEventDispatcher.getMulticaster().dictChanged(myAppDictionary);

    restartInspections();
  }

  @NotNull
  public static List<String> getBundledDictionaries() {
    final ArrayList<String> dictionaries = new ArrayList<>();
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      ContainerUtil.addAll(dictionaries, provider.getBundledDictionaries());
    }
    return dictionaries;
  }

  @NotNull
  public List<String> getSuggestions(@NotNull String text) {
    return suggestionProvider.getSuggestions(text);
  }

  @NotNull
  protected List<String> getRawSuggestions(@NotNull String word) {
    if (!spellChecker.isCorrect(word)) {
      List<String> suggestions = spellChecker.getSuggestions(word, settings.getCorrectionsLimit(), MAX_METRICS);
      if (!suggestions.isEmpty()) {
        if (Strings.isCapitalized(word)) {
          Strings.capitalize(suggestions);
        }
        else if (Strings.isUpperCase(word)) {
          Strings.upperCase(suggestions);
        }
        Set<String> unique = new LinkedHashSet<>(suggestions);
        return unique.size() < suggestions.size() ? new ArrayList<>(unique) : suggestions;
      }
    }
    return Collections.emptyList();
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
    return Stream.of(Extensions.getExtensions(CustomDictionaryProvider.EP_NAME))
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
    return myProjectDictinaryPath;
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

    public CustomDictFileListener(@NotNull SpellCheckerSettings settings) {mySettings = settings;}

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

      if (!spellChecker.isDictionaryLoad(path) || mySettings.getDisabledDictionariesPaths().contains(path)) return;

      spellChecker.removeDictionary(path);
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
        spellChecker.removeDictionariesRecursively(systemDependentPath);
        mySettings.getCustomDictionariesPaths().removeIf(dict -> isAncestor(systemDependentPath, dict, false));
        mySettings.getDisabledDictionariesPaths().removeIf(dict -> isAncestor(systemDependentPath, dict, false));
        restartInspections();
      }
    }

    private void loadCustomDictionaries(@NotNull VirtualFile file) {
      final String path = toSystemDependentName(file.getPath());
      if (!affectCustomDicts(path)) return;

      visitChildrenRecursively(file, new VirtualFileVisitor() {
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