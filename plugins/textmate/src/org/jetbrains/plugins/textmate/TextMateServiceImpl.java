package org.jetbrains.plugins.textmate;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.plugins.textmate.editor.TextMateSnippet;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.language.SnippetsRegistry;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.Preferences;
import org.jetbrains.plugins.textmate.language.preferences.PreferencesRegistry;
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistry;
import org.jetbrains.plugins.textmate.language.preferences.TextMateShellVariable;
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateCustomTextAttributes;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateEmulatedTheme;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTheme;
import org.jetbrains.plugins.textmate.plist.CompositePlistReader;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TextMateServiceImpl extends TextMateService {
  private static boolean ourBuiltinBundlesDisabled;

  private final AtomicBoolean myInitialized = new AtomicBoolean(false);
  
  private final Map<String, TextMateCustomTextAttributes> myCustomHighlightingColors = new HashMap<>();

  private final PlistReader myPlistReader = new CompositePlistReader();
  private final BundleFactory myBundleFactory = new BundleFactory(myPlistReader);
  private final TextMateSyntaxTable mySyntaxTable = new TextMateSyntaxTable();
  private final Map<String, TextMateTheme> myThemeHashMap = new HashMap<>();
  private final SnippetsRegistry mySnippetsRegistry = new SnippetsRegistry();
  private final PreferencesRegistry myPreferencesRegistry = new PreferencesRegistry();
  private final ShellVariablesRegistry myShellVariablesRegistry = new ShellVariablesRegistry();
  private final Map<String, String> myExtensionsMapping = new THashMap<>();
  @NonNls private static final String PREINSTALLED_THEMES_PATH =
    FileUtil.join(PathManager.getCommunityHomePath(), "plugins", "textmate", "lib", "themes");
  @NonNls private static final String INSTALLED_THEMES_PATH = FileUtil.join(PathManager.getPluginsPath(), "textmate", "lib", "themes");
  @NonNls public static final String PREINSTALLED_BUNDLES_PATH =
    FileUtil.toSystemIndependentName(FileUtil.join(PathManager.getCommunityHomePath(), "plugins", "textmate", "lib", "bundles"));
  @NonNls public static final String INSTALLED_BUNDLES_PATH = FileUtil.toSystemIndependentName(FileUtil.join(PathManager.getPluginsPath(), "textmate", "lib", "bundles"));
  private final Set<TextMateBundleListener> myListeners = new HashSet<>();

  @Override
  public void registerEnabledBundles() {
    doRegisterEnabledBundles(true);
  }

  private void doRegisterEnabledBundles(boolean fireEvents) {
    TextMateSettings settings = TextMateSettings.getInstance();
    if (settings == null) {
      return;
    }
    if (!ourBuiltinBundlesDisabled) {
      loadBuiltinBundles(settings);
    }
    THashMap<String, String> newExtensionsMapping = new THashMap<>();
    for (BundleConfigBean bundleConfigBean : settings.getBundles()) {
      if (bundleConfigBean.isEnabled()) {
        boolean result = registerBundle(LocalFileSystem.getInstance().findFileByPath(bundleConfigBean.getPath()), newExtensionsMapping);
        if (!result) {
          Notifications.Bus.notify(new Notification("TextMate Bundles", "TextMate bundle load error",
                                                    "Bundle " + bundleConfigBean.getName() + " can't be registered",
                                                    NotificationType.ERROR, null));
        }
      }
    }
    if (!myExtensionsMapping.equals(newExtensionsMapping)) {
      myExtensionsMapping.clear();
      myExtensionsMapping.putAll(newExtensionsMapping);
      if (fireEvents && !newExtensionsMapping.isEmpty()) {
        fireFileTypesChangedEvent();
      }
    }
  }

  private static void fireFileTypesChangedEvent() {
    TransactionGuard.getInstance().submitTransactionLater(ApplicationManager.getApplication(), () ->
      ApplicationManager.getApplication().runWriteAction(() -> {
        FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
        fileTypeManager.fireBeforeFileTypesChanged();
        fileTypeManager.fireFileTypesChanged();
      }));
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


  @Override
  public void unregisterAllBundles() {
    myExtensionsMapping.clear();
    myPreferencesRegistry.clear();
    myCustomHighlightingColors.clear();
    mySyntaxTable.clear();
    mySnippetsRegistry.clear();
    myShellVariablesRegistry.clear();
  }

  @Override
  public void addListener(@NotNull TextMateBundleListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull TextMateBundleListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void clearListeners() {
    myListeners.clear();
  }

  @Override
  @NotNull
  public PlistReader getPlistReader() {
    return myPlistReader;
  }

  @NotNull
  @Override
  public Map<String, TextMateCustomTextAttributes> getCustomHighlightingColors() {
    ensureInitialized();
    return myCustomHighlightingColors;
  }

  @NotNull
  @Override
  public List<Preferences> getPreferencesForSelector(@NotNull String selector) {
    ensureInitialized();
    return myPreferencesRegistry.getPreferences(selector);
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
    final String scopeName = myExtensionsMapping.get(extension.toString());
    return StringUtil.isNotEmpty(scopeName) ? new TextMateLanguageDescriptor(scopeName, mySyntaxTable.getSyntax(scopeName)) : null;
  }

  @Override
  public boolean registerTheme(@Nullable VirtualFile themeFile) {
    if (themeFile == null || !themeFile.isValid()) {
      return false;
    }
    synchronized (myThemeHashMap) {
      try {
        final TextMateTheme theme = TextMateTheme.load(myPlistReader.read(themeFile.getInputStream()));
        if (theme != TextMateTheme.EMPTY_THEME) {
          myThemeHashMap.put(theme.getName(), theme);
          for (TextMateBundleListener listener : myListeners) {
            listener.colorSchemeChanged();
          }
          return true;
        }
        else {
          return false;
        }
      }
      catch (IOException e) {
        return false;
      }
    }
  }

  @NotNull
  @Override
  public String[] getThemeNames() {
    ensureInitialized();
    synchronized (myThemeHashMap) {
      return ArrayUtilRt.toStringArray(myThemeHashMap.keySet());
    }
  }

  @Override
  @NotNull
  public TextMateTheme getCurrentTheme() {
    ensureInitialized();
    if (Registry.is("textmate.theme.emulation")) {
      return TextMateEmulatedTheme.THEME;
    }
    synchronized (myThemeHashMap) {
      String currentIdeaSchemeName = SchemeManager.getDisplayName(EditorColorsManager.getInstance().getGlobalScheme());
      String textmateSchemeName = TextMateSettings.getInstance().getTextMateThemeName(currentIdeaSchemeName, this);
      String schemeName = myThemeHashMap.containsKey(textmateSchemeName) ? textmateSchemeName : TextMateSettings.DEFAULT_THEME_NAME;
      TextMateTheme scheme = myThemeHashMap.get(schemeName);
      return scheme != null ? scheme : TextMateTheme.EMPTY_THEME;
    }
  }

  @Override
  public void reloadThemesFromDisk() {
    synchronized (myThemeHashMap) {
      myThemeHashMap.clear();
    }
    Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      if (application.isDisposed()) return;

      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      VirtualFile themesDirectory = ObjectUtils.chooseNotNull(
        fileSystem.findFileByPath(INSTALLED_THEMES_PATH),
        fileSystem.findFileByPath(PREINSTALLED_THEMES_PATH));

      if (themesDirectory != null) {
        final VirtualFile finalThemesDirectory = themesDirectory;
        Runnable reloadThemes = () -> {
          if (application.isDisposed()) return;
          synchronized (myThemeHashMap) {
            if (finalThemesDirectory.isValid()) {
              for (VirtualFile themeFile : finalThemesDirectory.getChildren()) {
                registerTheme(themeFile);
              }
            }
          }
        };
        themesDirectory.refresh(true, false, () -> application.executeOnPooledThread(reloadThemes));
      }
    });
  }

  @Override
  @Nullable
  public Bundle createBundle(@Nullable VirtualFile directory) {
    if (directory != null && directory.isInLocalFileSystem()) {
      final String path = directory.getCanonicalPath();
      if (path != null) {
        return myBundleFactory.fromDirectory(new File(path));
      }
    }
    return null;
  }

  private void ensureInitialized() {
    if (myInitialized.compareAndSet(false, true)) {
      doRegisterEnabledBundles(false);
    }
  }

  private boolean registerBundle(@Nullable VirtualFile directory, @NotNull THashMap<String, String> extensionsMapping) {
    final Bundle bundle = createBundle(directory);
    if (bundle != null) {
      registerLanguageSupport(bundle, extensionsMapping);
      registerPreferences(bundle);
      registerSnippets(bundle);
      registerThemes(bundle);
      return true;
    }
    return false;
  }

  private void registerThemes(@NotNull Bundle bundle) {
    synchronized (myThemeHashMap) {
      for (File themeFile : bundle.getThemeFiles()) {
        VirtualFile virtualFile = VfsUtil.findFileByIoFile(themeFile, true);
        registerTheme(virtualFile);
      }
    }
  }

  private void registerSnippets(@NotNull Bundle bundle) {
    for (File snippetFile : bundle.getSnippetFiles()) {
      try {
        TextMateSnippet snippet = PreferencesReadUtil.loadSnippet(snippetFile, myPlistReader.read(snippetFile));
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
        for (Pair<String, Plist> settingsPair : bundle.loadPreferenceFile(preferenceFile)) {
          if (settingsPair != null) {
            myPreferencesRegistry.fillFromPList(settingsPair.first, settingsPair.second);
            myShellVariablesRegistry.fillVariablesFromPlist(settingsPair.first, settingsPair.second);
            readCustomHighlightingColors(settingsPair.first, settingsPair.second);
          }
        }
      }
      catch (IOException e) {
        LOG.debug("Can't load textmate preferences file: " + preferenceFile.getPath());
      }
    }
  }

  private void readCustomHighlightingColors(@NotNull String scopeName, @NotNull Plist preferencesPList) {
    final TextAttributes textAttributes = new TextAttributes();
    final boolean hasHighlightingSettings = PreferencesReadUtil.fillTextAttributes(textAttributes, preferencesPList, null);
    if (hasHighlightingSettings) {
      final double backgroundAlpha = PreferencesReadUtil.getBackgroundAlpha(preferencesPList);
      myCustomHighlightingColors.put(scopeName, new TextMateCustomTextAttributes(textAttributes, backgroundAlpha));
    }
  }

  private void registerLanguageSupport(@NotNull Bundle bundle, @NotNull THashMap<String, String> extensionsMapping) {
    for (File grammarFile : bundle.getGrammarFiles()) {
      try {
        Plist plist = myPlistReader.read(grammarFile);
        String rootScopeName = mySyntaxTable.loadSyntax(plist);
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
  public static void disableBuiltinBundles(Disposable disposable) {
    ourBuiltinBundlesDisabled = true;
    TextMateService.getInstance().unregisterAllBundles();
    TextMateService.getInstance().registerEnabledBundles();
    Disposer.register(disposable, () -> {
      ourBuiltinBundlesDisabled = false;
      TextMateService.getInstance().unregisterAllBundles();
      TextMateService.getInstance().registerEnabledBundles();
    });
  }
}