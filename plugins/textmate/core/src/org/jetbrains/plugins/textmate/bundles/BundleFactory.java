package org.jetbrains.plugins.textmate.bundles;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.io.File;
import java.io.IOException;

public class BundleFactory {
  private final PlistReader myPlistReader;

  public BundleFactory(PlistReader plistReader) {
    myPlistReader = plistReader;
  }

  /**
   * Create bundle object from directory.
   * Return {code}null{code} if bundle type can't be defined or
   * if IO exception occurred while reading directory.
   *
   * @return Bundle object or null
   */
  @Nullable
  public Bundle fromDirectory(@NotNull File directory) throws IOException {
    final BundleType type = BundleType.fromDirectory(directory);
    return switch (type) {
      case TEXTMATE -> fromTextMateBundle(directory);
      case SUBLIME -> new Bundle(directory.getName(), directory.getPath(), type);
      case VSCODE -> new VSCBundle(directory.getName(), directory.getPath());
      default -> null;
    };
  }

  private Bundle fromTextMateBundle(File directory) throws IOException {
    File infoPlist = new File(directory, Constants.BUNDLE_INFO_PLIST_NAME);
    if (infoPlist.exists() && infoPlist.isFile()) {
      final Plist plist = myPlistReader.read(infoPlist);
      final String bundleName = plist.getPlistValue(Constants.NAME_KEY, directory.getName()).getString();
      return new Bundle(bundleName, directory.getPath(), BundleType.TEXTMATE);
    }
    return null;
  }
}
