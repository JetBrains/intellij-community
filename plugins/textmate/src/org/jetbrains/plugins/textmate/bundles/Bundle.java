package org.jetbrains.plugins.textmate.bundles;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.io.*;
import java.util.*;

import static java.util.Collections.emptyList;

/**
 * @deprecated use `TextMateService#readBundle` or `TextMateBundleReader`
 */
@Deprecated
public class Bundle {
  // all extensions should be lowercased
  @SuppressWarnings("SpellCheckingInspection") private static final FileFilter SYNTAX_FILES_FILTER = new BundleFilesFilter("tmlanguage", "plist", "tmlanguage.json");
  @SuppressWarnings("SpellCheckingInspection") private static final FileFilter PREFERENCE_FILES_FILTER = new BundleFilesFilter("tmpreferences", "plist");

  protected final String myName;
  protected final File bundleFile;
  protected final BundleType myType;

  public Bundle(@NotNull String name, @NotNull String bundle, @NotNull BundleType type) {
    myName = name;
    bundleFile = new File(bundle);
    myType = type;
  }

  public @NotNull String getName() {
    return myName;
  }

  /**
   * @deprecated use `TextMateService#readBundle#readGrammars` or `TextMateBundleReader`
   */
  @Deprecated(forRemoval = true)
  public @NotNull Collection<File> getGrammarFiles() {
    switch (myType) {
      case TEXTMATE -> {
        return getFilesInBundle("Syntaxes", SYNTAX_FILES_FILTER);
      }
      case SUBLIME -> {
        return getFilesInBundle("", SYNTAX_FILES_FILTER);
      }
    }
    throw new IllegalArgumentException("Only textmate and sublime bundles are supported. Use TextMateBundleReader instead.");
  }

  /**
   * @deprecated use `TextMateService#readBundle#readPreferences` or `TextMateBundleReader`
   */
  @Deprecated(forRemoval = true)
  public @NotNull Collection<File> getPreferenceFiles() {
    switch (myType) {
      case TEXTMATE -> {
        return getFilesInBundle("Preferences", PREFERENCE_FILES_FILTER);
      }
      case SUBLIME -> {
        return getFilesInBundle("", PREFERENCE_FILES_FILTER);
      }
    }
    throw new IllegalArgumentException("Only textmate and sublime bundles are supported. Use TextMateBundleReader instead.");
  }

  /**
   * @deprecated use `TextMateService#readBundle#readSnippets` or `TextMateBundleReader`
   */
  @Deprecated(forRemoval = true)
  public @NotNull Collection<File> getSnippetFiles() {
    switch (myType) {
      case TEXTMATE -> {
        return getFilesInBundle("Snippets", new BundleFilesFilter(Constants.TEXTMATE_SNIPPET_EXTENSION, "plist"));
      }
      case SUBLIME -> {
        return emptyList();
      }
    }
    throw new IllegalArgumentException("Only textmate and sublime bundles are supported. Use TextMateBundleReader instead.");
  }

  private @NotNull Collection<File> getFilesInBundle(@NotNull String path, @Nullable FileFilter filter) {
    File[] files = null;
    try {
      files = new File(bundleFile, path).listFiles(filter);
    }
    catch (SecurityException ignore) { }
    return files != null && files.length > 0 ? Set.of(files) : Collections.emptySet();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Bundle bundle = (Bundle)o;

    return FileUtilRt.filesEqual(bundleFile, bundle.bundleFile);
  }

  @Override
  public int hashCode() {
    return FileUtilRt.pathHashCode(bundleFile.getPath());
  }

  @Override
  public String toString() {
    return "Bundle{name='" + myName + "', path='" + bundleFile + "', type=" + myType + '}';
  }


  /**
   * @deprecated use `TextMateService#readBundle#readGrammars` or `TextMateBundleReader`
   */
  @Deprecated(forRemoval = true)
  public Collection<String> getExtensions(@NotNull File file, @NotNull Plist plist) {
    return plist.getPlistValue(Constants.FILE_TYPES_KEY, emptyList()).getStringArray();
  }

  private static final class BundleFilesFilter implements FileFilter {
    private final Set<String> myExtensions;

    private BundleFilesFilter(String... extensions) {
      myExtensions = Set.of(extensions);
    }

    @Override
    public boolean accept(@NotNull File path) {
      return myExtensions.contains(FileUtilRt.getExtension(path.getName()).toLowerCase(Locale.US));
    }
  }
}
