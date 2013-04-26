/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * User: anna
 * Date: 17-Jun-2009
 */
package com.intellij.spellchecker;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SpellCheckerSeveritiesProvider extends SeveritiesProvider {
  public static final HighlightSeverity TYPO = new HighlightSeverity("TYPO", HighlightSeverity.INFORMATION.myVal + 5);

  @Override
  @NotNull
  public List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {
    final List<HighlightInfoType> result = new ArrayList<HighlightInfoType>();

    final TextAttributes attributes = new TextAttributes();

    attributes.setEffectType(EffectType.WAVE_UNDERSCORE);
    attributes.setEffectColor(new Color(0, 128, 0));

    result.add(new HighlightInfoType.HighlightInfoTypeImpl(TYPO,
               TextAttributesKey.createTextAttributesKey("TYPO", attributes)));
    return result;
  }

  @Override
  public Color getTrafficRendererColor(@NotNull TextAttributes textAttributes) {
    return Color.GREEN;
  }

  @Override
  public boolean isGotoBySeverityEnabled(HighlightSeverity minSeverity) {
    return TYPO != minSeverity;
  }
}