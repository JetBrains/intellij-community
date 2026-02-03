// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public final class SpellCheckerSeveritiesProvider extends SeveritiesProvider {
  public static final TextAttributesKey TYPO_KEY = TextAttributesKey.createTextAttributesKey("TYPO");
  public static final HighlightSeverity TYPO = new HighlightSeverity(
    "TYPO",
    HighlightSeverity.INFORMATION.myVal + 5,
    SpellCheckerBundle.INSTANCE.getLazyMessage("typo.severity"),
    SpellCheckerBundle.INSTANCE.getLazyMessage("typo.severity.capitalized"),
    SpellCheckerBundle.INSTANCE.getLazyMessage("typo.severity.count.message")
  );

  @Override
  public @NotNull List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {
    final class T extends HighlightInfoType.HighlightInfoTypeImpl implements HighlightInfoType.Iconable {
      private T(@NotNull HighlightSeverity severity, @NotNull TextAttributesKey attributesKey) {
        super(severity, attributesKey);
      }

      @Override
      public @NotNull Icon getIcon() {
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