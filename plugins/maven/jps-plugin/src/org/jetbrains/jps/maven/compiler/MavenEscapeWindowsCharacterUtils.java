// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.compiler;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public final class MavenEscapeWindowsCharacterUtils {

    // See org.apache.maven.shared.filtering.FilteringUtils.PATTERN
  private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile("^(.*)[a-zA-Z]:\\\\(.*)");

    /*
   * See org.apache.maven.shared.filtering.FilteringUtils.escapeWindowsPath()
   */
  public static void escapeWindowsPath(Appendable result, String val) throws IOException {
    if (!val.isEmpty() && WINDOWS_PATH_PATTERN.matcher(val).matches()) {
      // Adapted from StringUtils.replace in plexus-utils to accommodate pre-escaped backslashes.
      int start = 0, end;
      while ((end = val.indexOf('\\', start)) != -1) {
        result.append(val, start, end).append("\\\\");
        start = end + 1;

        if (val.indexOf('\\', end + 1) == end + 1) {
          start++;
        }
      }

      result.append(val, start, val.length());
    }
    else {
      result.append(val);
    }
  }
}
