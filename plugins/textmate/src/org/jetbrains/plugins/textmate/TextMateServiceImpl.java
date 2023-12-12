package org.jetbrains.plugins.textmate;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.textmate.bundles.*;
import org.jetbrains.plugins.textmate.configuration.TextMateBuiltinBundlesSettings;
import org.jetbrains.plugins.textmate.configuration.TextMatePersistentBundle;
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtilsKt;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.*;
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTextAttributesAdapter;
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static org.jetbrains.plugins.textmate.bundles.BundleReaderKt.readSublimeBundle;
import static org.jetbrains.plugins.textmate.bundles.BundleReaderKt.readTextMateBundle;
import static org.jetbrains.plugins.textmate.bundles.VSCBundleReaderKt.readVSCBundle;

public final class TextMateServiceImpl extends TextMateService {
  public static Path getBundledBundlePath() {
    return PluginPathManager.getPluginHome("textmate").toPath().resolve("lib/bundles").normalize();
  }

  private boolean ourBuiltinBundlesDisabled;

  private volatile boolean myInitialized;
  private final Lock myRegistrationLock = new ReentrantLock();

  private final Map<CharSequence, TextMateTextAttributesAdapter> myCustomHighlightingColors = new HashMap<>();
  private Map<TextMateFileNameMatcher, CharSequence> myExtensionMapping = new HashMap<>();
  private final TextMateSyntaxTable mySyntaxTable = new TextMateSyntaxTable();
  private final SnippetsRegistryImpl mySnippetRegistry = new SnippetsRegistryImpl();
  private final PreferencesRegistryImpl myPreferenceRegistry = new PreferencesRegistryImpl();
  private final ShellVariablesRegistryImpl myShellVariablesRegistry = new ShellVariablesRegistryImpl();
  private final Interner<CharSequence> myInterner = Interner.createWeakInterner();
  private final CoroutineScope myScope;

  public TextMateServiceImpl(CoroutineScope scope) {
    myScope = scope;
    Application application = ApplicationManager.getApplication();
    Runnable checkCancelled = application == null || application.isUnitTestMode() ? null : ProgressManager::checkCanceled;
    SyntaxMatchUtils.setCheckCancelledCallback(checkCancelled);
  }

  @Override
  public void reloadEnabledBundles() {
    registerBundles(true);
  }

  private void registerBundles(boolean fireEvents) {
    myRegistrationLock.lock();
    try {
      Map<TextMateFileNameMatcher, CharSequence> oldExtensionsMapping = new HashMap<>(myExtensionMapping);
      unregisterAllBundles();

      TextMateUserBundlesSettings settings = TextMateUserBundlesSettings.getInstance();
      if (settings == null) {
        return;
      }
      Map<TextMateFileNameMatcher, CharSequence> newExtensionsMapping = new ConcurrentHashMap<>();

      if (!ourBuiltinBundlesDisabled) {
        TextMateBuiltinBundlesSettings builtinBundlesSettings = TextMateBuiltinBundlesSettings.getInstance();
        if (builtinBundlesSettings != null) {
          Set<String> turnedOffBundleNames = builtinBundlesSettings.getTurnedOffBundleNames();
          List<TextMateBundleToLoad> builtInBundles = discoverBuiltinBundles(builtinBundlesSettings);
          List<TextMateBundleToLoad> bundlesToEnable = turnedOffBundleNames.isEmpty()
                                                       ? builtInBundles
                                                       : ContainerUtil.filter(builtInBundles,
                                                                              bundleToLoad -> !turnedOffBundleNames.contains(bundleToLoad.getName()));
          TextMateBundlesLoader.registerBundlesInParallel(myScope,
                                                          bundlesToEnable,
                                                          bundleToLoad -> registerBundle(Path.of(bundleToLoad.getPath()), newExtensionsMapping));
        }
      }

      Map<String, TextMatePersistentBundle> userBundles = settings.getBundles();
      if (!userBundles.isEmpty()) {
        List<@NotNull TextMateBundleToLoad> paths = ContainerUtil.mapNotNull(userBundles.entrySet(), entry -> {
          return entry.getValue().getEnabled() ? new TextMateBundleToLoad(entry.getValue().getName(), entry.getKey()) : null;
        });
        TextMateBundlesLoader.registerBundlesInParallel(myScope, paths, bundleToLoad -> {
          return registerBundle(Path.of(bundleToLoad.getPath()), newExtensionsMapping);
        }, bundleToLoad -> {
          String bundleName = bundleToLoad.getName();
          String errorMessage = TextMateBundle.message("textmate.cant.register.bundle", bundleName);
          new Notification("TextMate Bundles", TextMateBundle.message("textmate.bundle.load.error", bundleName), errorMessage, NotificationType.ERROR)
            .addAction(NotificationAction.createSimpleExpiring(TextMateBundle.message("textmate.disable.bundle.notification.action", bundleName), () -> settings.disableBundle(bundleToLoad.getPath())))
            .notify(null);
        });
      }

      if (fireEvents && !oldExtensionsMapping.equals(newExtensionsMapping)) {
        fireFileTypesChangedEvent("old mappings = " + oldExtensionsMapping + ", new mappings" + newExtensionsMapping, () -> {
          myExtensionMapping = newExtensionsMapping;
        });
      }
      else {
        myExtensionMapping = newExtensionsMapping;
      }
      mySyntaxTable.compact();
    }
    finally {
      myRegistrationLock.unlock();
    }
  }

  private static void fireFileTypesChangedEvent(@NonNls @NotNull String reason, @NotNull Runnable update) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
        fileTypeManager.makeFileTypesChange(reason, update);
      });
    }, ModalityState.nonModal());
  }

  @ApiStatus.Internal
  public static List<TextMateBundleToLoad> discoverBuiltinBundles(@NotNull TextMateBuiltinBundlesSettings builtinBundlesSettings) {
    List<TextMateBundleToLoad> builtinBundles = builtinBundlesSettings.getBuiltinBundles();
    if (builtinBundles.isEmpty()) {
      Path builtinBundlesPath = getBundledBundlePath();
      try (Stream<Path> files = Files.list(builtinBundlesPath)) {
        List<TextMateBundleToLoad> bundles = files
          .filter(file -> !StringUtil.startsWithChar(file.getFileName().toString(), '.'))
          .map(file -> new TextMateBundleToLoad(file.getFileName().toString(), file.toString()))
          .toList();
        builtinBundlesSettings.setBuiltinBundles(bundles);
        return bundles;
      }
      catch (Throwable e) {
        LOG.warn("Couldn't list builtin textmate bundles at " + builtinBundlesPath, e);
        return Collections.emptyList();
      }
    }
    else {
      return builtinBundles;
    }
  }

  private void unregisterAllBundles() {
    myExtensionMapping.clear();
    myPreferenceRegistry.clear();
    myCustomHighlightingColors.clear();
    mySyntaxTable.clear();
    mySnippetRegistry.clear();
    myShellVariablesRegistry.clear();
  }

  @Override
  public @NotNull Map<CharSequence, TextMateTextAttributesAdapter> getCustomHighlightingColors() {
    ensureInitialized();
    return myCustomHighlightingColors;
  }

  @Override
  public @NotNull ShellVariablesRegistry getShellVariableRegistry() {
    ensureInitialized();
    return myShellVariablesRegistry;
  }

  @Override
  public @NotNull SnippetsRegistry getSnippetRegistry() {
    ensureInitialized();
    return mySnippetRegistry;
  }

  @Override
  public @NotNull PreferencesRegistry getPreferenceRegistry() {
    ensureInitialized();
    return myPreferenceRegistry;
  }

  @Override
  public @Nullable TextMateLanguageDescriptor getLanguageDescriptorByFileName(@NotNull CharSequence fileName) {
    if (Strings.isEmpty(fileName)) {
      return null;
    }

    ensureInitialized();
    CharSequence scopeName = myExtensionMapping.get(new TextMateFileNameMatcher.Name(StringUtil.toLowerCase(fileName.toString())));
    if (!Strings.isEmpty(scopeName)) {
      return new TextMateLanguageDescriptor(scopeName, mySyntaxTable.getSyntax(scopeName));
    }

    Iterator<CharSequence> extensionsIterator = TextMateEditorUtilsKt.fileNameExtensions(fileName).iterator();
    while (extensionsIterator.hasNext()) {
      TextMateLanguageDescriptor descriptor = getLanguageDescriptorByExtension(extensionsIterator.next());
      if (descriptor != null) {
        return descriptor;
      }
    }
    return null;
  }

  @Override
  public @Nullable TextMateLanguageDescriptor getLanguageDescriptorByExtension(@Nullable CharSequence extension) {
    if (Strings.isEmpty(extension)) {
      return null;
    }

    ensureInitialized();
    CharSequence scopeName = myExtensionMapping.get(new TextMateFileNameMatcher.Extension(StringUtil.toLowerCase(extension.toString())));
    return !Strings.isEmpty(scopeName) ? new TextMateLanguageDescriptor(scopeName, mySyntaxTable.getSyntax(scopeName)) : null;
  }

  @Override
  public @NotNull Map<TextMateFileNameMatcher, CharSequence> getFileNameMatcherToScopeNameMapping() {
    ensureInitialized();
    return Collections.unmodifiableMap(myExtensionMapping);
  }

  @Override
  public @Nullable TextMateBundleReader readBundle(@Nullable Path directory) {
    if (directory != null) {
      BundleType bundleType = BundleType.detectBundleType(directory);
      return switch (bundleType) {
        case TEXTMATE -> readTextMateBundle(directory);
        case SUBLIME -> readSublimeBundle(directory);
        case VSCODE -> readVSCBundle(relativePath -> {
          try {
            return Files.newInputStream(directory.resolve(relativePath));
          }
          catch (NoSuchFileException e) {
            TextMateService.LOG.warn("Cannot find referenced file `" + relativePath + "` in bundle `" + directory + "`");
            return null;
          }
          catch (Throwable e) {
            TextMateService.LOG.warn("Cannot read referenced file `" + relativePath + "` in bundle `" + directory + "`", e);
            return null;
          }
        });
        case UNDEFINED -> null;
      };
    }
    return null;
  }

  private void ensureInitialized() {
    if (!myInitialized) {
      myRegistrationLock.lock();
      try {
        if (myInitialized) return;
        registerBundles(false);
        myInitialized = true;
      }
      finally {
        myRegistrationLock.unlock();
      }
    }
  }

  private boolean registerBundle(@Nullable Path directory, @NotNull Map<TextMateFileNameMatcher, CharSequence> extensionMapping) {
    TextMateBundleReader reader = readBundle(directory);
    if (reader != null) {
      registerLanguageSupport(reader, extensionMapping);
      registerPreferences(reader);
      registerSnippets(reader);
      return true;
    }
    return false;
  }

  private void registerSnippets(@NotNull TextMateBundleReader reader) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // it's used in internal mode only (see org.jetbrains.plugins.textmate.editor.TextMateCustomLiveTemplate.isApplicable),
      // do not register to save some memory and loading time
      Iterator<TextMateSnippet> snippetsIterator = reader.readSnippets().iterator();
      while (snippetsIterator.hasNext()) {
        mySnippetRegistry.register(snippetsIterator.next());
      }
    }
  }

  private void registerPreferences(@NotNull TextMateBundleReader reader) {
    Iterator<TextMatePreferences> preferencesIterator = reader.readPreferences().iterator();
    while (preferencesIterator.hasNext()) {
      TextMatePreferences preferences = preferencesIterator.next();
      CharSequence scopeName = myInterner.intern(preferences.getScopeName());
      Set<TextMateBracePair> internedHighlightingPairs = ObjectUtils.doIfNotNull(preferences.getHighlightingPairs(), pairs ->
        ContainerUtil.map2Set(pairs, p -> new TextMateBracePair(myInterner.intern(p.getLeft()), myInterner.intern(p.getRight())))
      );
      Set<TextMateAutoClosingPair> internedSmartTypingPairs = ObjectUtils.doIfNotNull(preferences.getSmartTypingPairs(), pairs ->
        ContainerUtil.map2Set(pairs, p -> new TextMateAutoClosingPair(myInterner.intern(p.getLeft()), myInterner.intern(p.getRight()), p.getNotIn()))
      );
      Set<TextMateBracePair> internedSurroundingPairs = ObjectUtils.doIfNotNull(preferences.getSurroundingPairs(), pairs ->
        ContainerUtil.map2Set(pairs, p -> new TextMateBracePair(myInterner.intern(p.getLeft()), myInterner.intern(p.getRight())))
      );
      myPreferenceRegistry.addPreferences(new Preferences(scopeName,
                                                          internedHighlightingPairs,
                                                          internedSmartTypingPairs,
                                                          internedSurroundingPairs,
                                                          preferences.getAutoCloseBefore(),
                                                          preferences.getIndentationRules(),
                                                          preferences.getOnEnterRules()));
      for (TextMateShellVariable variable : preferences.getVariables()) {
        myShellVariablesRegistry.addVariable(variable);
      }
      TextMateTextAttributes customHighlightingAttributes = preferences.getCustomHighlightingAttributes();
      if (customHighlightingAttributes != null) {
        myCustomHighlightingColors.put(scopeName, new TextMateTextAttributesAdapter(scopeName, customHighlightingAttributes));
      }
    }
  }

  private void registerLanguageSupport(@NotNull TextMateBundleReader reader,
                                       @NotNull Map<TextMateFileNameMatcher, CharSequence> extensionMapping) {
    Iterator<TextMateGrammar> grammarIterator = reader.readGrammars().iterator();
    while (grammarIterator.hasNext()) {
      TextMateGrammar grammar = grammarIterator.next();
      CharSequence rootScopeName = mySyntaxTable.loadSyntax(grammar.getPlist().getValue(), myInterner);
      if (rootScopeName != null) {
        for (TextMateFileNameMatcher fileNameMatcher : grammar.getFileNameMatchers()) {
          if (fileNameMatcher instanceof TextMateFileNameMatcher.Name) {
            String newName = StringUtil.toLowerCase(((TextMateFileNameMatcher.Name)fileNameMatcher).getFileName());
            extensionMapping.put(((TextMateFileNameMatcher.Name)fileNameMatcher).copy(newName), rootScopeName);
          }
          else {
            extensionMapping.put(fileNameMatcher, rootScopeName);
          }
        }
      }
    }
  }

  @TestOnly
  public void disableBuiltinBundles(Disposable disposable) {
    ourBuiltinBundlesDisabled = true;
    TextMateService.getInstance().reloadEnabledBundles();
    myInitialized = true;
    Disposer.register(disposable, () -> {
      ourBuiltinBundlesDisabled = false;
      unregisterAllBundles();
      myInitialized = false;
    });
  }
}
