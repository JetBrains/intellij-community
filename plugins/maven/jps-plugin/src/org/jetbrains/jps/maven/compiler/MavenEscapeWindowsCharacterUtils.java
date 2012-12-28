/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.maven.compiler;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenEscapeWindowsCharacterUtils {

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
