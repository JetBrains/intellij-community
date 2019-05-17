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
package org.intellij.plugins.markdown.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.StripTrailingSpacesFilter;
import com.intellij.openapi.editor.StripTrailingSpacesFilterFactory;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.openapi.project.Project;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownStripTrailingSpacesFilterFactory extends StripTrailingSpacesFilterFactory {

  @NotNull
  @Override
  public StripTrailingSpacesFilter createFilter(@Nullable Project project, @NotNull Document document) {
    Language documentLanguage = PsiBasedStripTrailingSpacesFilter.getDocumentLanguage(document);
    if (documentLanguage != null && documentLanguage.is(MarkdownLanguage.INSTANCE)) {
      return StripTrailingSpacesFilter.NOT_ALLOWED;
    }
    return StripTrailingSpacesFilter.ALL_LINES;
  }
}
