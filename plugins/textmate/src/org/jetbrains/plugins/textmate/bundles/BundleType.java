package org.jetbrains.plugins.textmate.bundles;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;

import java.io.File;

public enum BundleType {
  TEXTMATE("Syntaxes", "Preferences", "Snippets", Constants.TEXTMATE_SNIPPET_EXTENSION, "plist"),
  SUBLIME("", "", "", Constants.SUBLIME_SNIPPET_EXTENSION),
  VSCODE("syntaxes", "", "snippets", "json"),
  UNDEFINED();

  private final String mySyntaxesPath;
  private final String myPreferencesPath;
  private final String mySnippetsPath;
  private final String[] mySnippetFileExtensions;

  BundleType() {
    this("", "", "");
  }

  BundleType(@NotNull String syntaxesPath,
             @NotNull String preferencesPath,
             @NotNull String snippetsPath,
             String... snippetFileExtension) {
    mySyntaxesPath = syntaxesPath;
    myPreferencesPath = preferencesPath;
    mySnippetsPath = snippetsPath;
    mySnippetFileExtensions = snippetFileExtension;
  }

  @NotNull
  String getPreferencesPath() {
    return myPreferencesPath;
  }

  @NotNull
  String getSyntaxesPath() {
    return mySyntaxesPath;
  }

  @NotNull
  String getSnippetsPath() {
    return mySnippetsPath;
  }

  String @NotNull [] getSnippetFileExtensions() {
    return mySnippetFileExtensions;
  }

  /**
   * Define bundle type by directory.
   *
   * @param directory Bundle directory.
   * @return bundle type.
   * Returns {@link this#UNDEFINED} if passed file doesn't exists or it is not directory
   * or if it doesn't fit to textmate or sublime package.
   */
  @NotNull
  static BundleType fromDirectory(@Nullable File directory) {
    if (directory != null && directory.exists() && directory.isDirectory()) {
      if ("tmBundle".equalsIgnoreCase(FileUtilRt.getExtension(directory.getName()))) {
        return TEXTMATE;
      }
      File packageJson = new File(directory, Constants.PACKAGE_JSON_NAME);
      if (packageJson.exists() && packageJson.isFile()) {
        return VSCODE;
      }
      File infoPlist = new File(directory, Constants.BUNDLE_INFO_PLIST_NAME);
      final boolean hasInfoPlistFile = infoPlist.exists() && infoPlist.isFile();
      final File[] children = directory.listFiles();
      if (children != null) {
        for (File child : children) {
          if (hasInfoPlistFile && child.isDirectory() && "Syntaxes".equalsIgnoreCase(child.getName())) {
            return TEXTMATE;
          }
          final String fileExtension = FileUtilRt.getExtension(child.getName());
          if ("tmLanguage".equalsIgnoreCase(fileExtension) || "tmPreferences".equalsIgnoreCase(fileExtension)) {
            return SUBLIME;
          }
        }
      }

      if (hasInfoPlistFile) {
        return TEXTMATE;
      }
    }
    return UNDEFINED;
  }
}
