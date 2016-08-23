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
package com.jetbrains.python;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider;
import com.intellij.openapi.options.BeanConfigurable;

public class PythonFoldingOptionsProvider extends BeanConfigurable<PythonFoldingSettings> implements CodeFoldingOptionsProvider {
  protected PythonFoldingOptionsProvider(PythonFoldingSettings settings) {
    super(settings);
    checkBox("Long string literals", settings::isCollapseLongStrings, v->settings.COLLAPSE_LONG_STRINGS=v);
    checkBox("Long collection literals", settings::isCollapseLongCollections, v->settings.COLLAPSE_LONG_COLLECTIONS=v);
    checkBox("Sequential comments", settings::isCollapseSequentialComments, v->settings.COLLAPSE_SEQUENTIAL_COMMENTS=v);
  }
}
