package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.usageView.UsageInfo;

import javax.swing.*;
import java.awt.*;

/**
 * Navigates through the search results
 */
public final class ReplacementPreviewDialog extends DialogWrapper {
  private Editor replacement;

  private final Project project;
  private RangeHighlighter hilighter;
  private Editor editor;
  private static final TextAttributes attributes = new TextAttributes();

  static {
    attributes.setBackgroundColor( new Color(162,3,229,32) );
  }

  public ReplacementPreviewDialog(final Project project, UsageInfo info, String replacementString) {
    super(project,true);

    setTitle(SSRBundle.message("structural.replace.preview"));
    setOKButtonText(SSRBundle.message("replace.preview.oktext"));
    this.project = project;
    init();

    final PsiElement element = info.getElement();
    Segment range = info.getSegment();
    hilight(element.getContainingFile().getVirtualFile(), range.getStartOffset(), range.getEndOffset());
    UIUtil.setContent(replacement, replacementString,0,-1,project);

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    if (profile != null) {
      UIUtil.updateHighlighter(replacement, profile);
    }
  }

  private void hilight(VirtualFile file,int start, int end) {
    removeHilighter();

    editor = FileEditorManager.getInstance(project).openTextEditor(
      new OpenFileDescriptor(project, file),
      false
    );
    hilighter = editor.getMarkupModel().addRangeHighlighter(
      start,
      end,
      HighlighterLayer.SELECTION - 100,
      attributes,
      HighlighterTargetArea.EXACT_RANGE
    );
  }

  private void removeHilighter() {
    if (hilighter!=null && hilighter.isValid()) {
      hilighter.dispose();
      hilighter = null;
      editor = null;
    }
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.strucuturalsearch.plugin.replace.ReplacementPreviewDialog";
  }

  protected JComponent createCenterPanel() {
    JComponent centerPanel = new JPanel( new BorderLayout() );

    PsiFile file;
    replacement = UIUtil.createEditor(
      PsiDocumentManager.getInstance(project).getDocument(
        file = JavaPsiFacade.getInstance(project).getElementFactory().createCodeBlockCodeFragment(
          "",null,true
        )
      ),
      project,
      true,
      null
    );

    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file,false);

    centerPanel.add(BorderLayout.NORTH,new JLabel(SSRBundle.message("replacement.code")) );
    centerPanel.add(BorderLayout.CENTER,replacement.getComponent() );
    centerPanel.setMaximumSize(new Dimension(640,480));

    return centerPanel;
  }

  public void dispose() {
    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(
      PsiDocumentManager.getInstance(project).getPsiFile(replacement.getDocument()),
      true
    );

    EditorFactory.getInstance().releaseEditor(replacement);
    removeHilighter();

    super.dispose();
  }
}

