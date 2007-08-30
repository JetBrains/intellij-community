/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Aug 9, 2007
 * Time: 4:45:40 PM
 */
package com.intellij.xml.refactoring;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.RefactoringUtil;
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

  private PsiElement myElement;
  private Editor myEditor;
  private JLabel myTitleLabel;
  private NameSuggestionsField myNameSuggestionsField;
  private String myHelpID;
  private XmlTag myTag;
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
    myHelpID = HelpID.getRenameHelpID(myTag);
  }

  protected void dispose() {
    myNameSuggestionsField.removeDataChangedListener(myNameChangedListener);
    super.dispose();
  }

  protected boolean hasHelpAction() {
    return false;
  }

  protected boolean isToSearchInCommentsForRename() {
    return RefactoringSettings.getInstance().isToSearchInCommentsForRename(myTag);
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
    myNameSuggestionsField = new NameSuggestionsField(new String[] { myTag.getName() }, myProject, StdFileTypes.PLAIN_TEXT, myEditor);
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
    String prefix = myNameSuggestionsField.getName();

    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    final PsiReference reference = myTag.getReference();
    if (reference != null) {
      final Object[] variants = reference.getVariants();
      for (Object variant : variants) {
        LookupItemUtil.addLookupItem(set, variant, "");
      }

      LookupItem[] lookupItems = set.toArray(new LookupItem[set.size()]);
      editor.getCaretModel().moveToOffset(prefix.length());
      editor.getSelectionModel().removeSelection();
      LookupManager.getInstance(getProject()).showLookup(editor, lookupItems, prefix, null, new CharFilter() {
        public int accept(char c, final String prefix) {
          if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
          return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
        }
      });
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
    return myNameSuggestionsField.getComponent();
  }

  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(), BorderFactory.createEmptyBorder(4, 8, 4, 8)));

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
    return myNameSuggestionsField.getName().trim();
  }

  protected void validateButtons() {
    super.validateButtons();

    getPreviewAction().setEnabled(false);
  }

  protected boolean areButtonsValid() {
    final String newName = getNewName();
    return RefactoringUtil.isValidName(myProject, myTag, newName);
  }
}