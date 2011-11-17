/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.SystemInfo;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;

public class IdeaSubversionConfigurationDirectory {
  private IdeaSubversionConfigurationDirectory() {
  }

  public static String getPath() {
    final File standard = SVNWCUtil.getDefaultConfigurationDirectory();
    if (SystemInfo.isWindows) {
      return standard.getAbsolutePath();
    }
    return standard.getParent() + File.separator + standard.getName() + "_IDEA";
  }
}
