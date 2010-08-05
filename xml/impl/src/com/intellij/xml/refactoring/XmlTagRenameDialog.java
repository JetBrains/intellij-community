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
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Aug 9, 2007
 * Time: 4:45:40 PM
 */
package com.intellij.xml.refactoring;

import com.intellij.codeInsight.completion.simple.SimpleLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;
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
import java.util.LinkedHashSet;
import java.util.Set;

public class XmlTagRenameDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.refactoring.XmlTagRenameDialog");
  private static final String REFACTORING_NAME = RefactoringBundle.message("rename.title");

  private final PsiElement myElement;
  private final Editor myEditor;
  private JLabel myTitleLabel;
  private NameSuggestionsField myNameSuggestionsField;
  private String myHelpID;
  private final XmlTag myTag;
  private NameSuggestionsField.DataChanged myNameChangedListener;

  public XmlTagRenameDialog(@NotNull final Editor editor, @NotNull final PsiElement element, @NotNull final XmlTag tag) {
    super(element.getProject(), true);

    myEditor = editor;
    myElement = element;
    myTag = tag;

    setTitle(REFACTORING_NAME);
    createNewNameComponent();

    init();

    myTitleLabel.setText(XmlBundle.message("rename.current.tag", getFullName(tag)));

    validateButtons();
  }

  protected void dispose() {
    myNameSuggestionsField.removeDataChangedListener(myNameChangedListener);
    super.dispose();
  }

  protected boolean hasHelpAction() {
    return false;
  }

  private static String getFullName(@NotNull final XmlTag tag) {
    final String name = UsageViewUtil.getDescriptiveName(tag);
    return (UsageViewUtil.getType(tag) + " " + name).trim();
  }

  public static void renameXmlTag(final Editor editor, @NotNull final PsiElement element, @NotNull final XmlTag tag) {
    final XmlTagRenameDialog dialog = new XmlTagRenameDialog(editor, element, tag);
    dialog.show();
  }

  private void createNewNameComponent() {
    myNameSuggestionsField = new NameSuggestionsField(new String[] { myTag.getName() }, myProject, FileTypes.PLAIN_TEXT, myEditor);
    myNameChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myNameSuggestionsField.addDataChangedListener(myNameChangedListener);

    myNameSuggestionsField.getComponent().registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        completeVariable(myNameSuggestionsField.getEditor());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void completeVariable(final Editor editor) {
    String prefix = myNameSuggestionsField.getEnteredName();

    Set<LookupElement> set = new LinkedHashSet<LookupElement>();
    final PsiReference reference = myTag.getReference();
    if (reference != null) {
      final Object[] variants = reference.getVariants();
      for (Object variant : variants) {
        set.add(new SimpleLookupItem(variant));
      }

      LookupElement[] lookupItems = set.toArray(new LookupElement[set.size()]);
      editor.getCaretModel().moveToOffset(prefix.length());
      editor.getSelectionModel().removeSelection();
      LookupManager.getInstance(getProject()).showLookup(editor, lookupItems, prefix);
    }
  }

  protected void doAction() {
    LOG.assertTrue(myElement.isValid());

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              myTag.setName(getNewName());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, RefactoringBundle.message("rename.title"), null);

    close(DialogWrapper.OK_EXIT_CODE);
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return null;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField.getFocusableComponent();
  }

  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createRoundedBorder(), BorderFactory.createEmptyBorder(4, 8, 4, 8)));

    myTitleLabel = new JLabel();
    panel.add(myTitleLabel);
    panel.add(Box.createVerticalStrut(8));
    panel.add(myNameSuggestionsField.getComponent());

    return panel;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  public String getNewName() {
    return myNameSuggestionsField.getEnteredName().trim();
  }

  protected void validateButtons() {
    super.validateButtons();

    getPreviewAction().setEnabled(false);
  }

  protected boolean areButtonsValid() {
    final String newName = getNewName();
    return !StringUtil.containsAnyChar(newName, "\t ;*'\"\\/,()^&<>={}"); // RenameUtil.isValidName(myProject, myTag, newName); // IDEADEV-34531
  }
}
