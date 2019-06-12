package org.jetbrains.plugins.textmate;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.util.Pair;
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.bundles.BundleFactory;
import org.jetbrains.plugins.textmate.configuration.BundleConfigBean;
import org.jetbrains.plugins.textmate.configuration.TextMateSettings;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtils;
import org.jetbrains.plugins.textmate.editor.TextMateSnippet;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.language.SnippetsRegistry;
import org.jetbrains.plugins.textmate.language.TextMateFileType;
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

public class TextMateServiceImpl extends TextMateService {
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
  public void registerEnabledBundles(boolean loadBuiltin) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doRegisterEnabledBundles(loadBuiltin);
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        if (!ApplicationManager.getApplication().isDisposed()) {
          doRegisterEnabledBundles(loadBuiltin);
        }
      });
    }
  }

  private void doRegisterEnabledBundles(boolean loadBuiltin) {
    final TextMateSettings settings = TextMateSettings.getInstance();
    if (settings == null) {
      return;
    }

    List<FileNameMatcher> matchers = new ArrayList<>();
    if (loadBuiltin) {
      loadBuiltinBundles(settings);
    }
    for (BundleConfigBean bundleConfigBean : settings.getBundles()) {
      if (bundleConfigBean.isEnabled()) {
        boolean result = registerBundle(LocalFileSystem.getInstance().findFileByPath(bundleConfigBean.getPath()), matchers);
        if (!result) {
          Notifications.Bus.notify(new Notification("TextMate Bundles", "TextMate bundle load error",
                                                    "Bundle " + bundleConfigBean.getName() + " can't be registered",
                                                    NotificationType.ERROR, null));
        }
      }
    }
    updateAssociations(matchers);
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
  public void unregisterAllBundles(boolean unregisterFileTypes) {
    if (unregisterFileTypes) {
      final FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
      ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
        for (FileNameMatcher matcher : fileTypeManager.getAssociations(TextMateFileType.INSTANCE)) {
          fileTypeManager.removeAssociation(TextMateFileType.INSTANCE, matcher, false);
        }
      }));
    }
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
    return myCustomHighlightingColors;
  }

  @NotNull
  @Override
  public List<Preferences> getPreferencesForSelector(@NotNull String selector) {
    return myPreferencesRegistry.getPreferences(selector);
  }

  @Nullable
  @Override
  public TextMateShellVariable getVariable(@NotNull String name, @NotNull EditorEx editor) {
    return myShellVariablesRegistry.getVariableValue(name, TextMateEditorUtils.getCurrentScopeSelector(editor));
  }

  @NotNull
  @Override
  public SnippetsRegistry getSnippetsRegistry() {
    return mySnippetsRegistry;
  }

  @Override
  @Nullable
  public TextMateLanguageDescriptor getLanguageDescriptorByFileName(@NotNull String fileName) {
    for (String extension : getExtensions(fileName)) {
      final TextMateLanguageDescriptor languageDescriptor = getLanguageDescriptorByExtension(extension);
      if (languageDescriptor != null) {
        return languageDescriptor;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public TextMateLanguageDescriptor getLanguageDescriptorByExtension(String extension) {
    final String scopeName = myExtensionsMapping.get(extension);
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
    synchronized (myThemeHashMap) {
      return ArrayUtilRt.toStringArray(myThemeHashMap.keySet());
    }
  }

  @Override
  @NotNull
  public TextMateTheme getCurrentTheme() {
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

  private boolean registerBundle(VirtualFile directory, List<FileNameMatcher> matchers) {
    final Bundle bundle = createBundle(directory);
    if (bundle != null) {
      registerLanguageSupport(bundle, matchers);
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

  private void registerLanguageSupport(@NotNull Bundle bundle, List<FileNameMatcher> matchers) {
    Set<String> newExtensions = new THashSet<>();
    for (File grammarFile : bundle.getGrammarFiles()) {
      try {
        final Plist plist = myPlistReader.read(grammarFile);
        String rootScopeName = mySyntaxTable.loadSyntax(plist);
        final List<String> extensions = bundle.getExtensions(grammarFile, plist);
        for (final String extension : extensions) {
          myExtensionsMapping.put(extension, rootScopeName);
          newExtensions.add(extension);
        }
      }
      catch (IOException e) {
        LOG.warn("Can't load textmate language file: " + grammarFile.getPath());
      }
    }
    registerTextMateExtensions(newExtensions, matchers);
  }

  private void updateAssociations(List<FileNameMatcher> matchers) {
    TransactionGuard.getInstance().submitTransactionLater(ApplicationManager.getApplication(), () -> {
      Set<FileNameMatcher> associationsToDelete = new THashSet<>();
      final FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
      for (FileNameMatcher nameMatcher : fileTypeManager.getAssociations(TextMateFileType.INSTANCE)) {
        final String typeKey;
        if (nameMatcher instanceof ExtensionFileNameMatcher) {
          typeKey = ((ExtensionFileNameMatcher)nameMatcher).getExtension();
        }
        else if (nameMatcher instanceof ExactFileNameMatcher) {
          typeKey = ((ExactFileNameMatcher)nameMatcher).getFileName();
        }
        else {
          continue;
        }
        if (!myExtensionsMapping.containsKey(typeKey)) {
          associationsToDelete.add(nameMatcher);
        }
      }

      if (matchers.isEmpty() && associationsToDelete.isEmpty()) return;

      ApplicationManager.getApplication().runWriteAction(() -> {
        for (FileNameMatcher matcher : matchers) {
          fileTypeManager.approveRemoval(matcher);
          fileTypeManager.associate(TextMateFileType.INSTANCE, matcher, false);
        }
        for (FileNameMatcher nameMatcher : associationsToDelete) {
          fileTypeManager.removeAssociation(TextMateFileType.INSTANCE, nameMatcher, false);
        }
        fileTypeManager.fireBeforeFileTypesChanged();
        fileTypeManager.fireFileTypesChanged();
      });
    });
  }

  private static void registerTextMateExtensions(@NotNull final Collection<String> extensions, List<FileNameMatcher> matchers) {
    FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
    for (String extension : extensions) {
      FileType registeredType = fileTypeManager.getFileTypeByFileName(extension);
      if (isTypeShouldBeReplacedByTextMateType(registeredType)) {
        matchers.add(FileTypeManager.parseFromString(extension));
      }

      registeredType = fileTypeManager.getFileTypeByExtension(extension);
      if (isTypeShouldBeReplacedByTextMateType(registeredType)) {
        matchers.add(new ExtensionFileNameMatcher(extension));
      }
    }
  }

  public static boolean isTypeShouldBeReplacedByTextMateType(FileType registeredType) {
    return registeredType == UnknownFileType.INSTANCE
           || registeredType == TextMateFileType.INSTANCE
           || registeredType == PlainTextFileType.INSTANCE;
  }

  public static Collection<String> getExtensions(@NotNull String name) {
    final ArrayList<String> result = ContainerUtil.newArrayList(name);
    int index = name.indexOf('.');
    while (index >= 0) {
      final String extension = name.substring(index + 1);
      if (extension.isEmpty()) break;
      result.add(extension);
      index = name.indexOf('.', index + 1);
    }
    return result;
  }
}