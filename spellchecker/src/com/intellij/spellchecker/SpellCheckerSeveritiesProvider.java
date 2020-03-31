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

package com.intellij.spellchecker;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class SpellCheckerSeveritiesProvider extends SeveritiesProvider {
  private static final TextAttributesKey TYPO_KEY = TextAttributesKey.createTextAttributesKey("TYPO");
  public static final HighlightSeverity TYPO = new HighlightSeverity("TYPO", HighlightSeverity.INFORMATION.myVal + 5);

  @Override
  @NotNull
  public List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {
    class T extends HighlightInfoType.HighlightInfoTypeImpl implements HighlightInfoType.Iconable{
      private T(@NotNull HighlightSeverity severity, @NotNull TextAttributesKey attributesKey) {
        super(severity, attributesKey);
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return AllIcons.General.InspectionsTypos;
      }
    }
    return Collections.singletonList(new T(TYPO, TYPO_KEY));
  }

  @Override
  public boolean isGotoBySeverityEnabled(HighlightSeverity minSeverity) {
    return TYPO != minSeverity;
  }
}