// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionListStep;
import com.intellij.codeInspection.QuickFix;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorShowQuickFixesAction extends AnAction {
  private final static Logger LOG = Logger.getInstance(ResourceBundleEditorShowQuickFixesAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ResourceBundleEditor editor = getEditor(e);
    LOG.assertTrue(editor != null);
    final PropertyBundleEditorStructureViewElement element = (PropertyBundleEditorStructureViewElement)editor.getSelectedElementIfOnlyOne();
    LOG.assertTrue(element != null);

    final PsiFile file = editor.getResourceBundle().getDefaultPropertiesFile().getContainingFile();
    final ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();

    boolean isQuickFixListEmpty = true;
    Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] descriptorsAndSources = element.getProblemDescriptors();
    for (Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey> p : descriptorsAndSources) {
      final ResourceBundleEditorProblemDescriptor d = p.getFirst();
      final HighlightDisplayKey sourceKey = p.getSecond();
      QuickFix[] fixes = d.getFixes();
      if (fixes != null) {
        for (int i = 0; i < fixes.length; i++) {
          intentions.inspectionFixesToShow.add(new HighlightInfo.IntentionActionDescriptor(new RBEQuickFixWrapper(d, i),
                                                                                           null,
                                                                                           null,
                                                                                           AllIcons.Actions.IntentionBulb,
                                                                                           sourceKey,
                                                                                           null,
                                                                                           null, null));
          isQuickFixListEmpty = false;
        }
      }
    }

    if (isQuickFixListEmpty) {
      return;
    }

    final Project project = e.getProject();
    LOG.assertTrue(project != null);
    JBPopupFactory
      .getInstance()
      .createListPopup(new IntentionListStep(null, null, file, project, CachedIntentions.create(project, file, null, intentions)))
      .showInBestPositionFor(e.getDataContext());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final ResourceBundleEditor editor = getEditor(e);
    e.getPresentation().setEnabledAndVisible(editor != null &&
                                             editor.getSelectedElementIfOnlyOne() instanceof PropertyStructureViewElement);
  }

  private static ResourceBundleEditor getEditor(@NotNull AnActionEvent e) {
    final FileEditor editor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    return editor instanceof ResourceBundleEditor ? (ResourceBundleEditor)editor : null;
  }

  private static final class RBEQuickFixWrapper implements IntentionAction {
    private final ResourceBundleEditorProblemDescriptor myDescriptor;
    private final int myIndex;

    private RBEQuickFixWrapper(ResourceBundleEditorProblemDescriptor descriptor, int index) {
      myDescriptor = descriptor;
      myIndex = index;
    }

    @Nls
    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getQuickFix().getFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      final QuickFix<ResourceBundleEditorProblemDescriptor> fix = getQuickFix();
      ThrowableRunnable<RuntimeException> fixAction = () -> fix.applyFix(project, myDescriptor);
      if (fix.startInWriteAction()) {
        WriteAction.run(fixAction);
      } else {
        fixAction.run();
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    private QuickFix<ResourceBundleEditorProblemDescriptor> getQuickFix() {
      return myDescriptor.getFixes()[myIndex];
    }
  }

}