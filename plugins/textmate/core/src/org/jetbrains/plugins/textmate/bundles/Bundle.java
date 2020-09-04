package org.jetbrains.plugins.textmate.bundles;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

import static java.util.Collections.emptyList;

public class Bundle {
  // all extensions should be lowercased
  private static final FileFilter SYNTAX_FILES_FILTER = new BundleFilesFilter("tmlanguage", "plist", "tmlanguage.json");
  private static final FileFilter PREFERENCE_FILES_FILTER = new BundleFilesFilter("tmpreferences", "plist");

  protected final String myName;
  protected final File bundleFile;
  protected final BundleType myType;

  public Bundle(@NotNull String name, @NotNull String bundle, @NotNull BundleType type) {
    myName = name;
    bundleFile = new File(bundle);
    myType = type;
  }

  @NlsSafe
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
    return files != null && files.length > 0 ? ContainerUtil.set(files) : Collections.emptySet();
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


  public Collection<String> getExtensions(@NotNull File file, @NotNull Plist plist) {
    return plist.getPlistValue(Constants.FILE_TYPES_KEY, emptyList()).getStringArray();
  }

  public List<Map.Entry<String, Plist>> loadPreferenceFile(@NotNull File file, @NotNull PlistReader plistReader) throws IOException {
    return Collections.singletonList(PreferencesReadUtil.retrieveSettingsPlist(plistReader.read(file)));
  }

  public static class BundleFilesFilter implements FileFilter {
    private final Set<String> myExtensions;

    public BundleFilesFilter(String... extensions) {
      myExtensions = ContainerUtil.set(extensions);
    }

    @Override
    public boolean accept(@NotNull File path) {
      return myExtensions.contains(FileUtilRt.getExtension(path.getName()).toLowerCase(Locale.US));
    }
  }
}
