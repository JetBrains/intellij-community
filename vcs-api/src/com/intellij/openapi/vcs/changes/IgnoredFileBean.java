/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 15:24:28
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Pattern;

public class IgnoredFileBean {
  private final String myPath;
  private final String myMask;
  private final Pattern myPattern;
  private final IgnoreSettingsType myType;
  private String myAbsolutePath;
  private boolean myPathIsAbsolute;

  IgnoredFileBean(String path, IgnoreSettingsType type) {
    myPath = path;
    myType = type;
    myMask = null; 
    myPattern = null;
  }

  IgnoredFileBean(String mask) {
    myType = IgnoreSettingsType.MASK;
    myMask = mask;
    if (mask == null) {
      myPattern = null;
    }
    else {
      myPattern = PatternUtil.fromMask(mask);
    }
    myPath = null;
  }

  @Nullable
  public String getPath() {
    return myPath;
  }

  @Nullable
  public String getMask() {
    return myMask;
  }

  public Pattern getPattern() {
    return myPattern;
  }

  public IgnoreSettingsType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IgnoredFileBean that = (IgnoredFileBean)o;

    if (myPath != null ? !myPath.equals(that.myPath) : that.myPath != null) return false;
    if (myMask != null ? !myMask.equals(that.myMask) : that.myMask != null) return false;
    if (myType != that.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPath != null ? myPath.hashCode() : 0;
    result = 31 * result + (myMask != null ? myMask.hashCode() : 0);
    result = 31 * result + myType.hashCode();
    return result;
  }

  public void initAbsolute() {
    if (myAbsolutePath == null && (myPath != null)) {
      final File file = new File(myPath);
      myAbsolutePath = file.getAbsolutePath();
      if (IgnoreSettingsType.UNDER_DIR.equals(myType)) {
        myAbsolutePath += File.separatorChar;
      }
      myPathIsAbsolute = file.isAbsolute();
    }
  }

  public boolean fileIsUnderMe(final String ioFileAbsPath, final File baseDir) {
    if (! IgnoreSettingsType.MASK.equals(myType)) {
      initAbsolute();
      if (myPathIsAbsolute) {
        return StringUtil.startsWithIgnoreCase(ioFileAbsPath, myAbsolutePath);
      }
      final File file = FileUtil.createFileByRelativePath(baseDir, myPath);
      if (file == null) return false;
      String absPath = file.getAbsolutePath();
      absPath = IgnoreSettingsType.UNDER_DIR.equals(myType) ? absPath + File.separatorChar : absPath;
      return StringUtil.startsWithIgnoreCase(ioFileAbsPath, absPath);
    }
    return false;
  }
}
