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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.usages.*;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

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
    ArrayList<Usage> usages = new ArrayList<Usage>();
    CommonProcessors.CollectProcessor<Usage> collect = new CommonProcessors.CollectProcessor<Usage>(usages);
    UsageViewPresentation presentation = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager().processUsages(element, collect);
    if (presentation == null) return;
    if (usages.isEmpty()) {
      HintManager.getInstance().showInformationHint(editor, FindBundle.message("find.usage.view.no.usages.text"));
    }
    else if (usages.size() == 1) {
      Usage usage = usages.iterator().next();
      usage.navigate(true);
      FileEditorLocation location = usage.getLocation();
      FileEditor newFileEditor = location == null ? null :location.getEditor();
      final Editor newEditor = newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
      if (newEditor != null) {
        //opening editor is performing in invokeLater
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            newEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
              public void run() {
                // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
                SwingUtilities.invokeLater(new Runnable() {
                  public void run() {
                    HintManager.getInstance().showInformationHint(newEditor, FindBundle.message("show.usages.only.usage"));
                  }
                });
              }
            });
          }
        });
      }
    }
    else {
      Processor<Usage> doNavigate = new Processor<Usage>() {
        public boolean process(final Usage usage) {
          usage.navigate(true);
          return false;
        }
      };
      final String title = presentation.getTabText();
      JBPopup popup = getUsagePopup(usages, title, doNavigate, project);
      if (popup != null) {
        popup.showInBestPositionFor(editor);
      }
    }
  }

  private static JBPopup getUsagePopup(List<Usage> usages, final String title, final Processor<Usage> processor, final Project project) {
    Usage[] arr = usages.toArray(new Usage[usages.size()]);
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setDetachedMode(true);
    final UsageViewImpl usageView = (UsageViewImpl)UsageViewManager.getInstance(project).createUsageView(new UsageTarget[0], arr, presentation, null);

    GroupNode root = usageView.getRoot();
    List<UsageNode> nodes = new ArrayList<UsageNode>();

    addUsageNodes(root, nodes);
    if (usages.size() == 1) {
      // usage view can filter usages down to one
      Usage usage = nodes.get(0).getUsage();
      processor.process(usage);
      return null;
    }

    final JList list = new JList(new Vector<UsageNode>(nodes));
    list.setCellRenderer(new ListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JPanel panel = new JPanel(new GridBagLayout());
        UsageNode usageNode = (UsageNode)value;
        int seq = appendGroupText((GroupNode)usageNode.getParent(), panel,list, value, index, isSelected);

        ColoredListCellRenderer usageRenderer = new ColoredListCellRenderer() {
          protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            UsageNode usageNode = (UsageNode)value;
            Usage usage = usageNode.getUsage();
            UsagePresentation presentation = usage.getPresentation();
            setIcon(presentation.getIcon());

            TextChunk[] text = presentation.getText();
            for (TextChunk textChunk : text) {
              append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
            }
          }
        };
        usageRenderer.setIpad(new Insets(0,0,0,0));
        usageRenderer.setBorder(null);
        usageRenderer.getListCellRendererComponent(list, value, index, isSelected, false);
        panel.add(usageRenderer, new GridBagConstraints(seq, 0, GridBagConstraints.REMAINDER, 0, 1, 0,
                                                        GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 1));
        panel.setBackground(list.getBackground());
        return panel;
      }
      private int appendGroupText(final GroupNode node, JPanel panel, JList list, Object value, int index, boolean isSelected) {
        if (node != null && node.getGroup() != null) {
          int seq = appendGroupText((GroupNode)node.getParent(), panel, list, value, index, isSelected);
          if (node.canNavigateToSource()) {
            ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
              protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
                UsageGroup group = node.getGroup();
                setIcon(group.getIcon(false));
                append(group.getText(usageView), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
            };
            renderer.setIpad(new Insets(0,0,0,0));
            renderer.setBorder(null);
            renderer.getListCellRendererComponent(list, value, index, isSelected, false);
            panel.add(renderer, new GridBagConstraints(seq, 0, 1, 0, 0, 0,
                                                       GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 1));
            return seq+1;
          }
        }
        return 0;
      }
    });

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        for (Object element : list.getSelectedValues()) {
          UsageNode node = (UsageNode)element;
          Usage usage = node.getUsage();
          processor.process(usage);
        }
      }
    };

    ListSpeedSearch speedSearch = new ListSpeedSearch(list) {
      protected String getElementText(final Object element) {
        StringBuilder text = new StringBuilder();
        UsageNode node = (UsageNode)element;
        Usage usage = node.getUsage();
        VirtualFile virtualFile = UsageListCellRenderer.getVirtualFile(usage);
        if (virtualFile != null) {
          text.append(virtualFile.getName());
        }
        TextChunk[] chunks = usage.getPresentation().getText();
        for (TextChunk chunk : chunks) {
          text.append(chunk.getText());
        }
        return text.toString();
      }
    };
    speedSearch.setComparator(new SpeedSearchBase.SpeedSearchComparator() {
      public void translatePattern(final StringBuilder buf, final String pattern) {
        final int len = pattern.length();
        for (int i = 0; i < len; ++i) {
          translateCharacter(buf, pattern.charAt(i));
        }
      }
    });

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }
    return builder.setItemChoosenCallback(runnable).createPopup();
  }

  private static void addUsageNodes(GroupNode root, List<UsageNode> nodes) {
    for (UsageNode node : root.getUsageNodes()) {
      node.setParent(root);
      nodes.add(node);
    }
    for (GroupNode groupNode : root.getSubGroups()) {
      groupNode.setParent(root);
      addUsageNodes(groupNode, nodes);
    }
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