// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.refactoring;

import com.intellij.codeInsight.completion.TagNameReferenceCompletionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class XmlTagRenameDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance(XmlTagRenameDialog.class);

  private final PsiElement myElement;
  private final Editor myEditor;
  private JLabel myTitleLabel;
  private NameSuggestionsField myNameSuggestionsField;
  private final XmlTag myTag;
  private NameSuggestionsField.DataChanged myNameChangedListener;

  public XmlTagRenameDialog(@NotNull final Editor editor, @NotNull final PsiElement element, @NotNull final XmlTag tag) {
    super(element.getProject(), true);

    myEditor = editor;
    myElement = element;
    myTag = tag;

    setTitle(RefactoringBundle.message("rename.title"));
    createNewNameComponent();

    init();

    myTitleLabel.setText(XmlBundle.message("xml.refactor.rename.current.tag", getFullName(tag)));

    validateButtons();
  }

  @Override
  protected void dispose() {
    myNameSuggestionsField.removeDataChangedListener(myNameChangedListener);
    super.dispose();
  }

  @Override
  protected boolean hasHelpAction() {
    return false;
  }

  private static String getFullName(@NotNull final XmlTag tag) {
    final String name = DescriptiveNameUtil.getDescriptiveName(tag);
    return (UsageViewUtil.getType(tag) + " " + name).trim();
  }

  public static void renameXmlTag(final Editor editor, @NotNull final PsiElement element, @NotNull final XmlTag tag) {
    final XmlTagRenameDialog dialog = new XmlTagRenameDialog(editor, element, tag);
    dialog.show();
  }

  private void createNewNameComponent() {
    myNameSuggestionsField = new NameSuggestionsField(new String[] { myTag.getName() }, myProject, FileTypes.PLAIN_TEXT, myEditor);
    myNameChangedListener = () -> validateButtons();
    myNameSuggestionsField.addDataChangedListener(myNameChangedListener);

    myNameSuggestionsField.getComponent().registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        completeVariable(myNameSuggestionsField.getEditor());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void completeVariable(final Editor editor) {
    String prefix = myNameSuggestionsField.getEnteredName();

    final PsiReference reference = myTag.getReference();
    if (reference instanceof TagNameReference) {
      LookupElement[] lookupItems = TagNameReferenceCompletionProvider.getTagNameVariants(myTag, myTag.getNamespacePrefix());
      editor.getCaretModel().moveToOffset(prefix.length());
      editor.getSelectionModel().removeSelection();
      LookupManager.getInstance(getProject()).showLookup(editor, lookupItems, prefix);
    }
  }

  @Override
  protected void doAction() {
    LOG.assertTrue(myElement.isValid());

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        myTag.setName(getNewName());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), RefactoringBundle.message("rename.title"), null);

    close(DialogWrapper.OK_EXIT_CODE);
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField.getFocusableComponent();
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    myTitleLabel = new JLabel();
    panel.add(myTitleLabel);
    panel.add(Box.createVerticalStrut(8));
    panel.add(myNameSuggestionsField.getComponent());

    return panel;
  }

  public String getNewName() {
    return myNameSuggestionsField.getEnteredName().trim();
  }

  @Override
  protected void validateButtons() {
    super.validateButtons();

    getPreviewAction().setEnabled(false);
  }

  @Override
  protected boolean areButtonsValid() {
    final String newName = getNewName();
    return !StringUtil.containsAnyChar(newName, "\t ;*'\"\\/,()^&<>={}"); // RenameUtil.isValidName(myProject, myTag, newName); // IDEADEV-34531
  }
}
