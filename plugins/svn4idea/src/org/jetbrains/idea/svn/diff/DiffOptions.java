/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.diff;

/**
 * @author Konstantin Kolosovsky.
 */
public class DiffOptions {

  private final boolean myIgnoreAllWhitespace;
  private final boolean myIgnoreAmountOfWhitespace;
  private final boolean myIgnoreEOLStyle;

  public DiffOptions(boolean ignoreAllWhitespace, boolean ignoreAmountOfWhiteSpace, boolean ignoreEOLStyle) {
    myIgnoreAllWhitespace = ignoreAllWhitespace;
    myIgnoreAmountOfWhitespace = ignoreAmountOfWhiteSpace;
    myIgnoreEOLStyle = ignoreEOLStyle;
  }

  public boolean isIgnoreAllWhitespace() {
    return myIgnoreAllWhitespace;
  }

  public boolean isIgnoreAmountOfWhitespace() {
    return myIgnoreAmountOfWhitespace;
  }

  public boolean isIgnoreEOLStyle() {
    return myIgnoreEOLStyle;
  }
}
