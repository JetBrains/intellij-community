/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import com.jetbrains.python.documentation.EpydocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class EpydocDocStringProvider extends DocStringProvider<EpydocString> {

  public static final String TAG_PREFIX = "@";

  @Override
  public EpydocString parseDocString(@NotNull Substring content) {
    return new EpydocString(content);
  }

  @NotNull
  @Override
  public DocStringUpdater updateDocString(@NotNull EpydocString docstring) {
    return new TagBasedDocStringUpdater<EpydocString>(docstring, TAG_PREFIX) {
      @Override
      public DocStringBuilder createDocStringBuilder() {
        return createDocString();
      }
    };
  }

  @NotNull
  @Override
  public DocStringBuilder createDocString() {
    return new TagBasedDocStringBuilder(TAG_PREFIX);
  }
}
