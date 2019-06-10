package org.jetbrains.plugins.textmate.bundles;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

public class Bundle {
  // all extensions should be lowercased
  private static final FileFilter SYNTAX_FILES_FILTER = new BundleFilesFilter("tmlanguage", "plist", "tmlanguage.json");
  private static final FileFilter PREFERENCE_FILES_FILTER = new BundleFilesFilter("tmpreferences", "plist");
  private static final FileFilter THEME_FILES_FILTER = new BundleFilesFilter("tmtheme", "plist");

  protected final String myName;
  protected final File bundleFile;
  protected final BundleType myType;

  public Bundle(@NotNull String name, @NotNull String bundle, @NotNull BundleType type) {
    myName = name;
    bundleFile = new File(bundle);
    myType = type;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Collection<File> getGrammarFiles() {
    return getFilesInBundle(myType.getSyntaxesPath(), SYNTAX_FILES_FILTER);
  }

  @NotNull
  public Collection<File> getPreferenceFiles() {
    return getFilesInBundle(myType.getPreferencesPath(), PREFERENCE_FILES_FILTER);
  }
  
  @NotNull
  public Collection<File> getThemeFiles() {
    return getFilesInBundle(myType.getThemesPath(), THEME_FILES_FILTER);
  }

  @NotNull
  public Collection<File> getSnippetFiles() {
    return getFilesInBundle(myType.getSnippetsPath(), new BundleFilesFilter(myType.getSnippetFileExtensions()));
  }

  @NotNull
  public BundleType getType() {
    return myType;
  }

  @NotNull
  private Collection<File> getFilesInBundle(@NotNull String path, @Nullable FileFilter filter) {
    File directory = new File(bundleFile, path);
    File[] files = null;
    try {
      files = directory.listFiles(filter);
    }
    catch (SecurityException ignore) {
    }
    return files != null && files.length > 0 ? ContainerUtil.newHashSet(files) : Collections.emptySet();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Bundle bundle = (Bundle)o;

    return FileUtil.filesEqual(bundleFile, bundle.bundleFile);
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(bundleFile);
  }

  @Override
  public String toString() {
    return "Bundle{" +
           "name='" + myName + '\'' +
           ", path='" + bundleFile + '\'' +
           ", type=" + myType +
           '}';
  }


  public List<String> getExtensions(File file, Plist plist) {
    return plist.getPlistValue(Constants.FILE_TYPES_KEY, emptyList()).getStringArray();
  }

  public List<Pair<String, Plist>> loadPreferenceFile(File file) throws IOException {
    return Collections.singletonList(PreferencesReadUtil.retrieveSettingsPlist(TextMateService.getInstance().getPlistReader().read(file)));
  }

  public static class BundleFilesFilter implements FileFilter {
    private final Set<String> myExtensions;

    public BundleFilesFilter(String... extensions) {
      myExtensions = ContainerUtil.newHashSet(extensions);
    }

    @Override
    public boolean accept(@NotNull File path) {
      return myExtensions.contains(StringUtil.toLowerCase(FileUtilRt.getExtension(path.getName())));
    }
  }
}
