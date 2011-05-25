package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.find.FindProgressIndicator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.structuralsearch.plugin.ui.DialogBase;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;

/**
 * Navigates through the search results (not used)
 */
public final class NavigateSearchResultsDialog extends DialogBase implements MatchResultSink {
  private final List<ReplacementInfo> replacements = new LinkedList<ReplacementInfo>(); // replacements found
  private int index; // index of shown match
  private Action previousMatch;

  private Editor replacement;

  private JComponent centerPanel;
  private JComponent southPanel;
  private MatchingProcess matchingProcess;

  private final Project project;
  private ReplaceOptions options;
  private final JButton replaceButton;
  private JButton replaceAll;

  private Replacer processor;
  private RangeHighlighter hilighter;
  private Editor editor;
  private static final TextAttributes attributes = new TextAttributes();
  private StatusBarEx statusBar;
  private boolean preview;
  private boolean ok;
  private ProgressIndicatorBase myMatchingProcess;

  static {
    attributes.setBackgroundColor( new Color(162,3,229,32) );
  }

  public NavigateSearchResultsDialog(final Project project, String replacementString) {
    super((Frame)WindowManager.getInstance().suggestParentWindow(project),true);

    setTitle(SSRBundle.message("structural.replace.preview.dialog.title"));
    setOKButtonText(SSRBundle.message("replace.preview.oktext"));
    preview = true;
    replaceButton = getOkButton();
    this.project = project;
    init();

    UIUtil.setContent(replacement, replacementString,0,-1,project);
  }

  public NavigateSearchResultsDialog(final Project project, boolean isReplace) {
    super((Frame)WindowManager.getInstance().suggestParentWindow(project),false);

    setTitle(SSRBundle.message(isReplace ?"structural.replace.title" :"structural.search.title"));
    setOKButtonText(SSRBundle.message("search.result.dialog.next.button"));
    this.project = project;
    index = -1;

    if (isReplace) {
      replaceButton = createJButtonForAction(
        new AbstractAction() {
          public void actionPerformed(ActionEvent e) {
            replacements.get(index).setReplacement( replacement.getDocument().getText() );
            replaceCurrentResult();

            if (index+1<replacements.size() || !matchingProcess.isEnded()) doOKAction();
            else setVisible(false);
          }
        }
      );

      replaceAll = createJButtonForAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          setVisible(false);
          doAll();
        }
      });

      replaceButton.setText(SSRBundle.message("replace.preview.oktext"));
      replaceAll.setText(SSRBundle.message("search.result.dialog.replace.all.button"));
    }
    else {
      replaceButton = null;
    }

    class PreviousMatchAction extends AbstractAction {
      PreviousMatchAction() {
        super(SSRBundle.message("search.result.dialog.previuos.button"));
        putValue(MNEMONIC_KEY,new Integer('P'));
      }
      public void actionPerformed(ActionEvent e) {
        if (index-1>=0) {
          --index;
          navigateOne(index);
        }
      }
    }

    previousMatch = new PreviousMatchAction();

    setOKActionEnabled(false);
    previousMatch.setEnabled(false);
    statusBar = (StatusBarEx)WindowManagerEx.getInstanceEx().getStatusBar(project);
    myMatchingProcess = new ProgressIndicatorBase() {
      public void cancel() {
        super.cancel();
        matchingProcess.stop();
      }
    };

    statusBar.addProgress(myMatchingProcess, new TaskInfo() {
      public String getProcessId() {
        return "<unknown>";
      }

      @NotNull
      public String getTitle() {
        return "";
      }

      public String getCancelText() {
        return null;
      }

      public String getCancelTooltipText() {
        return null;
      }

      public boolean isCancellable() {
        return false;
      }
    });
    
    init();
  }

  private void hilight(int start, int end) {
    removeHilighter();

    editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
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

  private void doAll() {
    while(index < replacements.size()) {
      replaceCurrentResult();
      ++index;
    }
  }

  private void replaceCurrentResult() {
    processor.replace(replacements.get(index));
  }

  // Performs the OK action of the dialog
  protected void doOKAction() {
    if (preview) {
      ok = true;
      hide();
      return;
    }

    if (!matchingProcess.isEnded() && index+1 == replacements.size()) {
      matchingProcess.resume();
      hide();
      return;
    }
    navigateNext();
  }

  private void navigateNext() {
    if (index+1 < replacements.size()) {
      ++index;
      navigateOne(index);
    }
  }

  protected JComponent createSouthPanel() {
    if (southPanel==null) {
      southPanel = new JPanel();
      JComponent ok = getOkButton();
      JComponent cancel = getCancelButton();

      southPanel.add(ok);
      if (!preview) southPanel.add(createJButtonForAction( previousMatch ));
      southPanel.add(cancel);
      if (!preview && replaceButton!=null) {
        southPanel.add(replaceButton);
        southPanel.add(replaceAll);
      }
    }

    return southPanel;
  }

  // Naviagates the result with given index
  private void navigateOne(int index) {
    List l = (List)replacements.get(index);
    SmartPsiElementPointer pointer = null;
    int start = 0;
    int end = 0;

    for (Object aL : l) {
      final SmartPsiElementPointer ptr = (SmartPsiElementPointer)aL;
      final PsiElement element = UIUtil.getNavigationElement(ptr.getElement());

      if (pointer == null) {
        pointer = ptr;
        start = element.getTextRange().getStartOffset();
      }
      end = element.getTextRange().getEndOffset();
    }

    if (replaceButton!=null) {
      if (pointer.getElement().isValid()) {
        final ReplacementInfo replacementInfo = replacements.get(index);
        UIUtil.setContent( replacement, replacementInfo.getReplacement(), 0, -1, project );

        final PsiElement firstMatch = replacementInfo.getMatch(0);
        if (firstMatch != null) {
          final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(firstMatch);
          if (profile != null) {
            UIUtil.updateHighlighter(editor, profile);
          }
        }

        replaceButton.setVisible(true);
        replacement.getComponent().setVisible(true);
      } else {
        replaceButton.setVisible(false);
        replacement.getComponent().setVisible(false);
      }
    }

    boolean hasNextMatch = index+ 1 < replacements.size();

    if (!matchingProcess.isEnded()) {
      hasNextMatch = true;
    }

    setOKActionEnabled( hasNextMatch );
    previousMatch.setEnabled( index > 0 );

    if (pointer != null) {
      UIUtil.navigate( pointer.getElement() );
      hilight(start,end);
    }
  }

  // Paerforms cancel action
  protected void doCancelAction() {
    if (preview) {
      hide();
      return;
    }

    clearResults();
    hide();
    if (!matchingProcess.isEnded()) matchingProcess.stop();
  }

  private void clearResults() {
    replacements.clear();

    if (processor!=null) {
      processor = null;
      if (myMatchingProcess != null) {
        myMatchingProcess.cancel();
      }
    }

    removeHilighter();
  }

  protected JComponent createCenterPanel() {
    if (centerPanel==null) {
      centerPanel = new JPanel( new BorderLayout() );

      if (replaceButton!=null) {
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

        centerPanel.add(BorderLayout.NORTH,new JLabel(SSRBundle.message("search.result.dialog.replacement.code.label")) );
        centerPanel.add(BorderLayout.CENTER,replacement.getComponent() );
      }
      centerPanel.setMaximumSize(new Dimension(640,480));
    }
    return centerPanel;
  }

  /**
   * Notifies sink about new match
   * @param result
   */
  public void newMatch(MatchResult result) {
    if (processor==null) {
      processor = new Replacer(project,options);
    }

    replacements.add(processor.buildReplacement(result));

    // new match before the end
    if (matchingProcess.isSuspended()) return;
    matchingProcess.pause();

    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          navigateNext();
          show();
        }
      }
    );
  }

  // Notifies sink about starting the matching for given element
  // @return false to stop matching process
  public void processFile(PsiFile file) {
  }

  public void setMatchingProcess(MatchingProcess _matchingProcess) {
    matchingProcess = _matchingProcess;
  }

  public void matchingFinished() {
    if (!matchingProcess.isSuspended() && index!=-1) {
      setOKActionEnabled(false);
      show();
      removeHilighter();
    }
  }

  public ProgressIndicator getProgressIndicator() {
    return new FindProgressIndicator(project, options.getMatchOptions().getScope().getDisplayName()) {
      {
        background();
      }
    };
  }

  public ReplaceOptions getOptions() {
    return options;
  }

  public void setOptions(ReplaceOptions options) {
    this.options = options;
  }

  public void dispose() {
    if (replacement!=null) {
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(
        PsiDocumentManager.getInstance(project).getPsiFile(replacement.getDocument()),
        true
      );

      EditorFactory.getInstance().releaseEditor(replacement);
    }

    super.dispose();
  }

  public boolean isOk() {
    return ok;
  }
}

