// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.ide.DataManager;
import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.DictionaryLayer;
import com.intellij.spellchecker.DictionaryLayersProvider;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class SaveTo implements SpellCheckerQuickFix, LowPriorityAction, DumbAware {
  @Nullable private DictionaryLayer myLayer = null;
  private String myWord;

  public SaveTo(@NotNull DictionaryLayer layer) {
    myLayer = layer;
  }

  public SaveTo(String word) {
    myWord = word;
  }

  public SaveTo(String word, @Nullable DictionaryLayer layer) {
    myWord = word;
    myLayer = layer;
  }

  @Override
  public @NotNull String getName() {
    return SpellCheckerBundle.message("save.0.to.dictionary.fix", myWord != null ? SpellCheckerBundle.message("0.in.quotes", myWord) : "");
  }

  @Override
  public @NotNull String getFamilyName() {
    return SpellCheckerBundle.message("save.0.to.dictionary.fix", "");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    var psiElement = descriptor.getPsiElement();
    var psiFile = psiElement.getContainingFile();
    var wordRange = descriptor.getTextRangeInElement().shiftRight(psiElement.getTextRange().getStartOffset());
    var wordToSave = myWord != null ? myWord : ProblemDescriptorUtil.extractHighlightedText(descriptor, descriptor.getPsiElement());
    applyFix(project, psiFile, wordRange, wordToSave, myLayer);
  }

  public static void applyFix(@NotNull Project project, PsiFile psiFile, TextRange wordRange, String wordToSave, @Nullable DictionaryLayer layer) {
    DataManager.getInstance()
      .getDataContextFromFocusAsync()
      .onSuccess(context -> {
        if (layer == null) {
          final List<String> dictList = new ArrayList<>(
            ContainerUtil.map(DictionaryLayersProvider.getAllLayers(project), it -> it.getName())
          );

          JBPopupFactory.getInstance()
            .createPopupChooserBuilder(dictList)
            .setTitle(SpellCheckerBundle.message("select.dictionary.title"))
            .setItemChosenCallback(
              (item) ->
                CommandProcessor.getInstance().executeCommand(
                  project,
                  () -> acceptWord(wordToSave, DictionaryLayersProvider.getLayer(project, item), psiFile, wordRange),
                  SpellCheckerBundle.message("save.0.to.dictionary.action", wordToSave),
                  null
                )
            )
            .setRenderer(SimpleListCellRenderer.create(
              (var label, @NlsContexts.Label var value, var index) -> {
                label.setText(value);
                label.setBorder(JBUI.Borders.empty(0, 15));
              }))
            .createPopup()
            .showInBestPositionFor(context);
        }
        else {
          acceptWord(wordToSave, layer, psiFile, wordRange);
        }
      });
  }

  private static void acceptWord(String word, @Nullable DictionaryLayer layer, PsiFile file, TextRange wordRange) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS);
    Project project = file.getProject();
    SpellCheckerManager.getInstance(project).acceptWordAsCorrect$intellij_spellchecker(word, file.getViewProvider().getVirtualFile(), project, layer);
    removeHighlightersWithExactRange(file.getViewProvider().getDocument(), project, wordRange, SpellCheckingInspection.SPELL_CHECKING_INSPECTION_TOOL_NAME);
  }

  /**
   * Remove all highlighters with exactly the given range from {@link DocumentMarkupModel} produced by given inspection.
   * This might be useful in quick fixes and intention actions to provide immediate feedback.
   * This method currently works in O(total highlighter count in file) time.
   */
  public static void removeHighlightersWithExactRange(@NotNull Document document, @NotNull Project project, @NotNull Segment range, @NotNull String inspectionToolId) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return;
    ThreadingAssertions.assertEventDispatchThread();
    MarkupModel model = DocumentMarkupModel.forDocument(document, project, false);
    if (model == null) return;

    for (RangeHighlighter highlighter : model.getAllHighlighters()) {
      if (TextRange.areSegmentsEqual(range, highlighter)) {
        var highlightInfo = HighlightInfo.fromRangeHighlighter(highlighter);
        if(highlightInfo == null || !inspectionToolId.equals(highlightInfo.getInspectionToolId())) {
          continue;
        }
        model.removeHighlighter(highlighter);
      }
    }
  }


  @Override
  public Icon getIcon(int flags) {
    return SpellcheckerIcons.Spellcheck;
  }

  public static String getFixName() {
    return SpellCheckerBundle.message("save.0.to.dictionary.fix", "");
  }
}
