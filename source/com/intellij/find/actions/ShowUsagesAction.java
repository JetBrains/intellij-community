package com.intellij.find.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.usages.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;

import javax.swing.*;
import java.util.*;

public class ShowUsagesAction extends AnAction {
  public ShowUsagesAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    Editor editor = e.getData(DataKeys.EDITOR);
    if (usageTargets == null) {
      chooseAmbiguousTarget(project, editor);
    }
    else {
      PsiElement element = ((PsiElement2UsageTargetAdapter)usageTargets[0]).getElement();
      showElementUsages(project, element, editor);
    }
  }

  private static void showElementUsages(final Project project, final PsiElement element, Editor editor) {
    Processor<Usage> processor = new Processor<Usage>() {
      public boolean process(final Usage usage) {
        usage.navigate(true);
        return false;
      }
    };
    ArrayList<Usage> usages = new ArrayList<Usage>();
    CommonProcessors.CollectProcessor<Usage> collect = new CommonProcessors.CollectProcessor<Usage>(usages);
    UsageViewPresentation presentation =
      ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager().processUsages(element, collect);

    if (usages.isEmpty()) {
      HintManager.getInstance().showInformationHint(editor, FindBundle.message("find.usage.view.no.usages.text"));
    }
    else if (usages.size() == 1) {
      Usage usage = usages.iterator().next();
      usage.navigate(true);
      FileEditorLocation location = usage.getLocation();
      FileEditor newFileEditor = location == null ? null :location.getEditor();
      Editor newEditor = newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
      if (newEditor != null) {
        HintManager.getInstance().showInformationHint(newEditor, FindBundle.message("show.usages.only.usage"));
      }
    }
    else {
      final String title = presentation.getTabText();
      getUsagePopup(usages, title, processor, project).showInBestPositionFor(editor);
    }
  }

  private static JBPopup getUsagePopup(List<Usage> usages, final String title, final Processor<Usage> processor, final Project project) {
    Collections.sort(usages, new Comparator<Usage>() {
      public int compare(final Usage o1, final Usage o2) {
        VirtualFile file1 = UsageListCellRenderer.getVirtualFile(o1);
        VirtualFile file2 = UsageListCellRenderer.getVirtualFile(o2);
        String name1 = file1 == null ? "" : file1.getName();
        String name2 = file2 == null ? "" : file2.getName();
        String s1 = name1 + o1;
        String s2 = name2 + o2;
        return s1.compareTo(s2);
      }
    });
    final JList list = new JList(new Vector<Usage>(usages));
    list.setCellRenderer(new UsageListCellRenderer());

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        for (Object element : list.getSelectedValues()) {
          processor.process((Usage)element);
        }
      }
    };

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }
    return builder.setItemChoosenCallback(runnable).createPopup();
  }


  private static void chooseAmbiguousTarget(final Project project, final Editor editor) {
    if (editor != null) {
      int offset = editor.getCaretModel().getOffset();
      if (GotoDeclarationAction.chooseAmbiguousTarget(project, editor, offset, new PsiElementProcessor<PsiElement>() {
        public boolean execute(final PsiElement element) {
          showElementUsages(project, element, editor);
          return false;
        }
      }, FindBundle.message("find.usages.ambiguous.title"))) return;
    }
    Messages.showMessageDialog(project,
          FindBundle.message("find.no.usages.at.cursor.error"),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
  }

  public void update(AnActionEvent e){
    FindUsagesInFileAction.updateFindUsagesAction(e);
    Editor editor = e.getData(DataKeys.EDITOR);
    if (editor == null) {
      e.getPresentation().setEnabled(false);
    }
  }
}