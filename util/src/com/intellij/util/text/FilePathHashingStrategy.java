package com.intellij.util.text;

import com.intellij.openapi.util.SystemInfo;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public class FilePathHashingStrategy {
  private FilePathHashingStrategy() {
  }

  public static TObjectHashingStrategy<String> create() {
    //noinspection unchecked
    return SystemInfo.isFileSystemCaseSensitive
           ? TObjectHashingStrategy.CANONICAL
           : new CaseInsensitiveStringHashingStrategy();
  }
}
