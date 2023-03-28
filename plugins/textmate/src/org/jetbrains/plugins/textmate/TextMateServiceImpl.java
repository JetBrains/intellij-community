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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.textmate.bundles.*;
import org.jetbrains.plugins.textmate.configuration.TextMateBuiltinBundlesSettings;
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings;
import org.jetbrains.plugins.textmate.configuration.TextMatePersistentBundle;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtilsKt;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.*;
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTextAttributesAdapter;
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
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
  private Map<TextMateFileNameMatcher, CharSequence> myExtensionsMapping = new HashMap<>();
  private final TextMateSyntaxTable mySyntaxTable = new TextMateSyntaxTable();
  private final SnippetsRegistryImpl mySnippetsRegistry = new SnippetsRegistryImpl();
  private final PreferencesRegistryImpl myPreferencesRegistry = new PreferencesRegistryImpl();
  private final ShellVariablesRegistryImpl myShellVariablesRegistry = new ShellVariablesRegistryImpl();
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
    myRegistrationLock.lock();
    try {
      Map<TextMateFileNameMatcher, CharSequence> oldExtensionsMapping = new HashMap<>(myExtensionsMapping);
      unregisterAllBundles();

      TextMateUserBundlesSettings settings = TextMateUserBundlesSettings.getInstance();
      if (settings == null) {
        return;
      }
      Map<TextMateFileNameMatcher, CharSequence> newExtensionsMapping = new HashMap<>();

      if (!ourBuiltinBundlesDisabled) {
        TextMateBuiltinBundlesSettings builtinBundlesSettings = TextMateBuiltinBundlesSettings.getInstance();
        if (builtinBundlesSettings != null) {
          Set<String> turnedOffBundleNames = builtinBundlesSettings.getTurnedOffBundleNames();
          for (Path bundlePath : discoverBuiltinBundles(builtinBundlesSettings)) {
            if (!turnedOffBundleNames.contains(bundlePath.getFileName().toString())) {
              VirtualFile bundleFile = LocalFileSystem.getInstance().findFileByNioFile(bundlePath);
              if (!registerBundle(bundleFile, newExtensionsMapping)) {
                LOG.error("Cannot load builtin textmate bundle", bundlePath.toString());
              }
            }
          }
        }
      }

      Map<String, TextMatePersistentBundle> userBundles = settings.getBundles();
      userBundles.forEach((path, bundle) -> {
        if (bundle.getEnabled()) {
          VirtualFile bundleFile = LocalFileSystem.getInstance().findFileByPath(path);
          boolean result = registerBundle(bundleFile, newExtensionsMapping);
          if (!result) {
            String bundleName = PathUtil.getFileName(path);
            String errorMessage = bundleFile != null ? TextMateBundle.message("textmate.cant.register.bundle", bundleName)
                                                     : TextMateBundle.message("textmate.cant.find.bundle", bundleName);
            new Notification("TextMate Bundles", TextMateBundle.message("textmate.bundle.load.error", bundleName), errorMessage,
                             NotificationType.ERROR)
              .addAction(NotificationAction
                           .createSimpleExpiring(TextMateBundle.message("textmate.disable.bundle.notification.action", bundleName),
                                                 () -> {
                                                   settings.disableBundle(path);
                                                 }))
              .notify(null);
          }
        }
      });

      if (fireEvents && !oldExtensionsMapping.equals(newExtensionsMapping)) {
        fireFileTypesChangedEvent("old mappings = " + oldExtensionsMapping + ", new mappings" + newExtensionsMapping, () -> {
          myExtensionsMapping = newExtensionsMapping;
        });
      }
      else {
        myExtensionsMapping = newExtensionsMapping;
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
    }, ModalityState.NON_MODAL);
  }

  private static List<Path> discoverBuiltinBundles(@NotNull TextMateBuiltinBundlesSettings builtinBundlesSettings) {
    List<Path> builtinBundles = builtinBundlesSettings.getBuiltinBundles();
    if (builtinBundles.isEmpty()) {
      Path builtinBundlesPath = getBundledBundlePath();
      try (Stream<Path> files = Files.list(builtinBundlesPath)) {
        List<Path> bundles = files.filter(file -> !StringUtil.startsWithChar(file.getFileName().toString(), '.')).toList();
        builtinBundlesSettings.setBuiltinBundles(bundles);
        return bundles;
      }
      catch (IOException e) {
        LOG.warn("Couldn't list builtin textmate bundles at " + builtinBundlesPath, e);
        return Collections.emptyList();
      }
    }
    else {
      return builtinBundles;
    }
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

  @Override
  public @NotNull ShellVariablesRegistry getShellVariableRegistry() {
    ensureInitialized();
    return myShellVariablesRegistry;
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
    if (Strings.isEmpty(fileName)) {
      return null;
    }

    ensureInitialized();
    CharSequence scopeName = myExtensionsMapping.get(new TextMateFileNameMatcher.Name(StringUtil.toLowerCase(fileName.toString())));
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
    CharSequence scopeName = myExtensionsMapping.get(new TextMateFileNameMatcher.Extension(StringUtil.toLowerCase(extension.toString())));
    return !Strings.isEmpty(scopeName) ? new TextMateLanguageDescriptor(scopeName, mySyntaxTable.getSyntax(scopeName)) : null;
  }

  @Override
  public @Nullable TextMateBundleReader readBundle(@Nullable VirtualFile directory) {
    if (directory != null) {
      Path path = directory.toNioPath();
      BundleType bundleType = BundleType.detectBundleType(path);
      return switch (bundleType) {
        case TEXTMATE -> readTextMateBundle(path);
        case SUBLIME -> readSublimeBundle(path);
        case VSCODE -> readVSCBundle(relativePath -> {
          try {
            return Files.newInputStream(path.resolve(relativePath));
          }
          catch (NoSuchFileException e) {
            return null;
          }
          catch (IOException e) {
            TextMateService.LOG.warn("Cannot find referenced file `" + relativePath + "`", e);
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

  private boolean registerBundle(@Nullable VirtualFile directory, @NotNull Map<TextMateFileNameMatcher, CharSequence> extensionsMapping) {
    TextMateBundleReader reader = readBundle(directory);
    if (reader != null) {
      registerLanguageSupport(reader, extensionsMapping);
      registerPreferences(reader);
      registerSnippets(reader);
      return true;
    }
    return false;
  }

  private void registerSnippets(@NotNull TextMateBundleReader reader) {
    Iterator<TextMateSnippet> snippetsIterator = reader.readSnippets().iterator();
    while (snippetsIterator.hasNext()) {
      mySnippetsRegistry.register(snippetsIterator.next());
    }
  }

  private void registerPreferences(@NotNull TextMateBundleReader reader) {
    Iterator<TextMatePreferences> preferencesIterator = reader.readPreferences().iterator();
    while (preferencesIterator.hasNext()) {
      TextMatePreferences preferences = preferencesIterator.next();
      CharSequence scopeName = myInterner.intern(preferences.getScopeName());
      myPreferencesRegistry.addPreferences(new Preferences(scopeName,
                                                           preferences.getHighlightingPairs(),
                                                           preferences.getSmartTypingPairs(),
                                                           preferences.getIndentationRules()));
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
                                       @NotNull Map<TextMateFileNameMatcher, CharSequence> extensionsMapping) {
    Iterator<TextMateGrammar> grammarIterator = reader.readGrammars().iterator();
    while (grammarIterator.hasNext()) {
      TextMateGrammar grammar = grammarIterator.next();
      CharSequence rootScopeName = mySyntaxTable.loadSyntax(grammar.getPlist(), myInterner);
      for (TextMateFileNameMatcher fileNameMatcher : grammar.getFileNameMatchers()) {
        extensionsMapping.put(fileNameMatcher, rootScopeName);
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
