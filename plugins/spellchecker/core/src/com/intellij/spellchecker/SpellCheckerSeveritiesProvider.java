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

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SpellCheckerSeveritiesProvider extends SeveritiesProvider {
  public static final HighlightSeverity MISSPELLED = new HighlightSeverity("MISSPELLED", 10);

  public List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {
    final List<HighlightInfoType> result = new ArrayList<HighlightInfoType>();

    final TextAttributes attributes = new TextAttributes();

    attributes.setEffectType(EffectType.WAVE_UNDERSCORE);
    attributes.setEffectColor(Color.GRAY.brighter());

    result.add(new HighlightInfoType.HighlightInfoTypeImpl(MISSPELLED,
               TextAttributesKey.createTextAttributesKey("MISSPELLED", attributes)));
    return result;
  }

  @Override
  public Color getTrafficRendererColor(TextAttributes textAttributes) {
    return Color.GREEN;
  }

  @Override
  public boolean isGotoBySeverityEnabled(HighlightSeverity minSeverity) {
    return MISSPELLED != minSeverity;
  }
}