package org.jetbrains.plugins.textmate.bundles;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.io.File;
import java.io.IOException;

public class BundleFactory {
  private static final Logger LOG = Logger.getInstance(BundleFactory.class);

  private final PlistReader myPlistReader;

  public BundleFactory(PlistReader plistReader) {
    myPlistReader = plistReader;
  }

  /**
   * Create bundle object from directory.
   * Return {code}null{code} if bundle type can't be defined or
   * if IO exception occurred while reading directory.
   *
   * @param directory
   * @return Bundle object or null
   */
  @Nullable
  public Bundle fromDirectory(@Nullable File directory) {
    final BundleType type = BundleType.fromDirectory(directory);
    switch (type) {
      case TEXTMATE:
        return fromTextMateBundle(directory);
      case SUBLIME:
        return new Bundle(directory.getName(), directory.getPath(), type);
      case VSCODE:
        return new VSCBundle(directory.getName(), directory.getPath());
      default:
        return null;
    }
  }

  private Bundle fromTextMateBundle(File directory) {
    File infoPlist = new File(directory, Constants.BUNDLE_INFO_PLIST_NAME);
    try {
      if (infoPlist.exists() && infoPlist.isFile()) {
        final Plist plist = myPlistReader.read(infoPlist);
        final String bundleName = plist.getPlistValue(Constants.NAME_KEY, directory.getName()).getString();
        return new Bundle(bundleName, directory.getPath(), BundleType.TEXTMATE);
      }
    }
    catch (IOException e) {
      LOG.debug("Can't read textmate bundle data", e);
    }
    return null;
  }
}
