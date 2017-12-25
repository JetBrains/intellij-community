// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;

import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.spellchecker.dictionary.*;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.engine.SpellCheckerEngine;
import com.intellij.spellchecker.engine.SpellCheckerFactory;
import com.intellij.spellchecker.engine.SuggestionProvider;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.spellchecker.state.AggregatedDictionaryState;
import com.intellij.spellchecker.util.Strings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.visitChildrenRecursively;

public class SpellCheckerManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.spellchecker.SpellCheckerManager");

  private static final int MAX_SUGGESTIONS_THRESHOLD = 5;
  private static final int MAX_METRICS = 1;

  private final Project project;
  private SpellCheckerEngine spellChecker;
  private AggregatedDictionary userDictionary;
  private final SuggestionProvider suggestionProvider = new BaseSuggestionProvider(this);
  private final SpellCheckerSettings settings;
  private final VirtualFileListener myCustomDictFileListener;

  public static SpellCheckerManager getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerManager.class);
  }

  public SpellCheckerManager(Project project, SpellCheckerSettings settings) {
    this.project = project;
    this.settings = settings;
    fullConfigurationReload();
    
    Disposer.register(project, this);

    myCustomDictFileListener = new CustomDictFileListener(settings);
    LocalFileSystem.getInstance().addVirtualFileListener(myCustomDictFileListener);
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

  public EditableDictionary getUserDictionary() {
    return userDictionary;
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
    final AggregatedDictionaryState dictionaryState = ServiceManager.getService(project, AggregatedDictionaryState.class);
    dictionaryState.addDictStateListener((dict) -> restartInspections());
    userDictionary = dictionaryState.getDictionary();
    spellChecker.addModifiableDictionary(userDictionary);
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
    final String transformed = spellChecker.getTransformation().transform(word);
    if (transformed != null) {
      userDictionary.addToDictionary(transformed);
      final PsiModificationTrackerImpl modificationTracker =
        (PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker();
      modificationTracker.incCounter();
    }
  }

  public void updateUserDictionary(@NotNull Collection<String> words) {
    userDictionary.replaceAll(words);
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
      List<String> suggestions = spellChecker.getSuggestions(word, MAX_SUGGESTIONS_THRESHOLD, MAX_METRICS);
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