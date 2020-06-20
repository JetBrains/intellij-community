package org.jetbrains.plugins.textmate;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.bundles.BundleFactory;
import org.jetbrains.plugins.textmate.configuration.BundleConfigBean;
import org.jetbrains.plugins.textmate.configuration.TextMateSettings;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtils;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.*;
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTextAttributesAdapter;
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils;
import org.jetbrains.plugins.textmate.plist.CompositePlistReader;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class TextMateServiceImpl extends TextMateService {
  private boolean ourBuiltinBundlesDisabled;

  private final AtomicBoolean myInitialized = new AtomicBoolean(false);

  private final Map<CharSequence, TextMateTextAttributesAdapter> myCustomHighlightingColors = new THashMap<>();
  private final Map<String, CharSequence> myExtensionsMapping = new THashMap<>();

  private final PlistReader myPlistReader = new CompositePlistReader();
  private final BundleFactory myBundleFactory = new BundleFactory(myPlistReader);
  private final TextMateSyntaxTable mySyntaxTable = new TextMateSyntaxTable();
  private final SnippetsRegistry mySnippetsRegistry = new SnippetsRegistry();
  private final PreferencesRegistry myPreferencesRegistry = new PreferencesRegistry();
  private final ShellVariablesRegistry myShellVariablesRegistry = new ShellVariablesRegistry();
  @NonNls public static final String PREINSTALLED_BUNDLES_PATH =
    FileUtil.toSystemIndependentName(FileUtil.join(PathManager.getCommunityHomePath(), "plugins", "textmate", "lib", "bundles"));
  @NonNls public static final String INSTALLED_BUNDLES_PATH =
    FileUtil.toSystemIndependentName(FileUtil.join(PathManager.getPluginsPath(), "textmate", "lib", "bundles"));
  private final Interner<CharSequence> myInterner = Interner.createWeakInterner();

  public TextMateServiceImpl() {
    Application application = ApplicationManager.getApplication();
    Runnable checkCancelled = application != null && !application.isUnitTestMode() ? ProgressManager::checkCanceled : null;
    SyntaxMatchUtils.setCheckCancelledCallback(checkCancelled);
  }

  @Override
  public void reloadEnabledBundles() {
    registerBundles(true);
  }

  private void registerBundles(boolean fireEvents) {
    Map<String, CharSequence> oldExtensionsMapping = new THashMap<>(myExtensionsMapping);
    unregisterAllBundles();

    TextMateSettings settings = TextMateSettings.getInstance();
    if (settings == null) {
      return;
    }
    if (!ourBuiltinBundlesDisabled) {
      loadBuiltinBundles(settings);
    }
    THashMap<String, CharSequence> newExtensionsMapping = new THashMap<>();
    for (BundleConfigBean bundleConfigBean : settings.getBundles()) {
      if (bundleConfigBean.isEnabled()) {
        boolean result = registerBundle(LocalFileSystem.getInstance().findFileByPath(bundleConfigBean.getPath()), newExtensionsMapping);
        if (!result) {
          Notifications.Bus.notify(new Notification("TextMate Bundles", TextMateBundle.message("textmate.bundle.load.error"),
                                                    TextMateBundle.message("textmate.cant.register.bundle", bundleConfigBean.getName()),
                                                    NotificationType.ERROR, null));
        }
      }
    }
    if (!oldExtensionsMapping.equals(newExtensionsMapping)) {
      Runnable update = () -> {
        myExtensionsMapping.clear();
        myExtensionsMapping.putAll(newExtensionsMapping);
        ContainerUtil.trimMap(myExtensionsMapping);
      };

      if (fireEvents) {
        fireFileTypesChangedEvent(update);
      }
      else {
        update.run();
      }
    }
    mySyntaxTable.compact();
    ContainerUtil.trimMap(myCustomHighlightingColors);
  }

  private static void fireFileTypesChangedEvent(@NotNull Runnable update) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
        fileTypeManager.fireBeforeFileTypesChanged();
        update.run();
        fileTypeManager.fireFileTypesChanged();
      });
    }, ModalityState.NON_MODAL);
  }

  private static void loadBuiltinBundles(TextMateSettings settings) {
    File bundles = new File(INSTALLED_BUNDLES_PATH);
    if (!bundles.exists() || !bundles.isDirectory()) {
      bundles = new File(PREINSTALLED_BUNDLES_PATH);
    }

    File[] files = bundles.listFiles();
    if (files == null) {
      LOG.warn("Missing builtin bundles, checked: \n" + INSTALLED_BUNDLES_PATH + "\n" + PREINSTALLED_BUNDLES_PATH);
      return;
    }

    TextMateSettings.TextMateSettingsState state = settings.getState();
    state = state == null ? new TextMateSettings.TextMateSettingsState() : state;

    List<BundleConfigBean> newBundles = new ArrayList<>(state.getBundles());
    for (File file : files) {
      if (file.getName().startsWith(".")) continue;
      String path = FileUtil.toSystemIndependentName(file.getPath());
      BundleConfigBean existing = ContainerUtil.find(state.getBundles(), (BundleConfigBean bundle) -> bundle.getPath().equals(path));
      if (existing != null) continue;
      newBundles.add(new BundleConfigBean(file.getName(), path, true));
    }
    state.setBundles(newBundles);
    settings.loadState(state);
  }

  private void unregisterAllBundles() {
    myExtensionsMapping.clear();
    myPreferencesRegistry.clear();
    myCustomHighlightingColors.clear();
    mySyntaxTable.clear();
    mySnippetsRegistry.clear();
    myShellVariablesRegistry.clear();
  }

  @NotNull
  @Override
  public Map<CharSequence, TextMateTextAttributesAdapter> getCustomHighlightingColors() {
    ensureInitialized();
    return myCustomHighlightingColors;
  }

  @Nullable
  @Override
  public TextMateShellVariable getVariable(@NotNull String name, @NotNull EditorEx editor) {
    ensureInitialized();
    return myShellVariablesRegistry.getVariableValue(name, TextMateEditorUtils.getCurrentScopeSelector(editor));
  }

  @NotNull
  @Override
  public SnippetsRegistry getSnippetsRegistry() {
    ensureInitialized();
    return mySnippetsRegistry;
  }

  @NotNull
  @Override
  public PreferencesRegistry getPreferencesRegistry() {
    ensureInitialized();
    return myPreferencesRegistry;
  }

  @Override
  @Nullable
  public TextMateLanguageDescriptor getLanguageDescriptorByFileName(@NotNull CharSequence fileName) {
    if (StringUtil.isEmpty(fileName)) return null;
    ensureInitialized();
    Ref<TextMateLanguageDescriptor> result = Ref.create();
    TextMateEditorUtils.processExtensions(fileName, extension -> {
      result.set(getLanguageDescriptorByExtension(extension));
      return result.isNull();
    });
    return result.get();
  }

  @Override
  @Nullable
  public TextMateLanguageDescriptor getLanguageDescriptorByExtension(@Nullable CharSequence extension) {
    if (StringUtil.isEmpty(extension)) return null;
    ensureInitialized();
    CharSequence scopeName = myExtensionsMapping.get(extension.toString());
    return !StringUtil.isEmpty(scopeName) ? new TextMateLanguageDescriptor(scopeName, mySyntaxTable.getSyntax(scopeName)) : null;
  }

  @Override
  @Nullable
  public Bundle createBundle(@Nullable VirtualFile directory) {
    if (directory != null && directory.isInLocalFileSystem()) {
      final String path = directory.getCanonicalPath();
      if (path != null) {
        try {
          return myBundleFactory.fromDirectory(new File(path));
        }
        catch (IOException e) {
          LOG.debug("Couldn't load bundle from " + path, e);
          return null;
        }
      }
    }
    return null;
  }

  private void ensureInitialized() {
    if (myInitialized.compareAndSet(false, true)) {
      registerBundles(false);
    }
  }

  private boolean registerBundle(@Nullable VirtualFile directory, @NotNull THashMap<String, CharSequence> extensionsMapping) {
    final Bundle bundle = createBundle(directory);
    if (bundle != null) {
      registerLanguageSupport(bundle, extensionsMapping);
      registerPreferences(bundle);
      registerSnippets(bundle);
      return true;
    }
    return false;
  }

  private void registerSnippets(@NotNull Bundle bundle) {
    for (File snippetFile : bundle.getSnippetFiles()) {
      try {
        TextMateSnippet snippet = PreferencesReadUtil.loadSnippet(snippetFile, myPlistReader.read(snippetFile), myInterner);
        if (snippet != null) {
          mySnippetsRegistry.register(snippet);
        }
      }
      catch (IOException e) {
        LOG.debug("Can't load textmate preferences file: " + snippetFile.getPath());
      }
    }
  }

  private void registerPreferences(@NotNull Bundle bundle) {
    for (File preferenceFile : bundle.getPreferenceFiles()) {
      try {
        for (Map.Entry<String, Plist> settingsPair : bundle.loadPreferenceFile(preferenceFile, myPlistReader)) {
          if (settingsPair != null) {
            CharSequence scopeName = myInterner.intern(settingsPair.getKey());
            myPreferencesRegistry.fillFromPList(scopeName, settingsPair.getValue());
            myShellVariablesRegistry.fillVariablesFromPlist(scopeName, settingsPair.getValue());
            readCustomHighlightingColors(scopeName, settingsPair.getValue());
          }
        }
      }
      catch (IOException e) {
        LOG.debug("Can't load textmate preferences file: " + preferenceFile.getPath());
      }
    }
  }

  private void readCustomHighlightingColors(@NotNull CharSequence scopeName, @NotNull Plist preferencesPList) {
    TextMateTextAttributes textAttributes = TextMateTextAttributes.fromPlist(preferencesPList);
    if (textAttributes != null) {
      myCustomHighlightingColors.put(scopeName, new TextMateTextAttributesAdapter(scopeName, textAttributes));
    }
  }

  private void registerLanguageSupport(@NotNull Bundle bundle, @NotNull THashMap<String, CharSequence> extensionsMapping) {
    for (File grammarFile : bundle.getGrammarFiles()) {
      try {
        Plist plist = myPlistReader.read(grammarFile);
        CharSequence rootScopeName = mySyntaxTable.loadSyntax(plist, myInterner);
        Collection<String> extensions = bundle.getExtensions(grammarFile, plist);
        for (String extension : extensions) {
          extensionsMapping.put(extension, rootScopeName);
        }
      }
      catch (IOException e) {
        LOG.warn("Can't load textmate language file: " + grammarFile.getPath());
      }
    }
  }

  @TestOnly
  public void disableBuiltinBundles(Disposable disposable) {
    ourBuiltinBundlesDisabled = true;
    TextMateService.getInstance().reloadEnabledBundles();
    myInitialized.set(true);
    Disposer.register(disposable, () -> {
      ourBuiltinBundlesDisabled = false;
      unregisterAllBundles();
      myInitialized.set(false);
    });
  }
}