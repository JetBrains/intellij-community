
package com.intellij.refactoring.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class InlineMethodDialog extends RefactoringDialog implements InlineOptions {
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");
  private PsiJavaCodeReferenceElement myReferenceElement;
  private final Editor myEditor;

  public static interface Callback {
    void run(InlineMethodDialog dialog);
  }

  private JLabel myMethodNameLabel = new JLabel();

  private final PsiMethod myMethod;
  private final boolean myInvokedOnReference;

  private JRadioButton myRbInlineAll;
  private JRadioButton myRbInlineThisOnly;

  public InlineMethodDialog(Project project, PsiMethod method, PsiJavaCodeReferenceElement ref, Editor editor) {
    super(project, true);
    myMethod = method;
    myReferenceElement = ref;
    myEditor = editor;
    myInvokedOnReference = ref != null;

    setTitle(REFACTORING_NAME);

    init();

    String methodText = PsiFormatUtil.formatMethod(myMethod,
                                                   PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    myMethodNameLabel.setText(RefactoringBundle.message("inline.method.method.label", methodText));
  }

  public boolean isInlineThisOnly() {
    return myRbInlineThisOnly.isSelected();
  }

  protected JComponent createNorthPanel() {
    return myMethodNameLabel;
  }

  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("inline.method.border.title")));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

    final String inlineAllText = myMethod.isWritable() ? RefactoringBundle.message("all.invocations.and.remove.the.method") :
                                 RefactoringBundle.message("all.invocations.in.project");
    myRbInlineAll = new JRadioButton();
    myRbInlineAll.setText(inlineAllText);
    myRbInlineAll.setSelected(true);
    myRbInlineThisOnly = new JRadioButton();
    myRbInlineThisOnly.setText(RefactoringBundle.message("this.invocation.only.and.keep.the.method"));

    optionsPanel.add(myRbInlineAll);
    optionsPanel.add(myRbInlineThisOnly);
    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInlineAll);
    bg.add(myRbInlineThisOnly);

    myRbInlineThisOnly.setEnabled(myInvokedOnReference);
    final boolean writable = myMethod.isWritable();
    if(myInvokedOnReference) {
      if (InlineMethodHandler.checkRecursive(myMethod)) {
        myRbInlineAll.setSelected(false);
        myRbInlineAll.setEnabled(false);
        myRbInlineThisOnly.setSelected(true);
      } else {
        if (writable) {
          final boolean inline_method_this = RefactoringSettings.getInstance().INLINE_METHOD_THIS;
          myRbInlineThisOnly.setSelected(inline_method_this);
          myRbInlineAll.setSelected(!inline_method_this);
        }
        else {
          myRbInlineAll.setSelected(false);
          myRbInlineThisOnly.setSelected(true);
        }
      }
    } else {
      myRbInlineAll.setSelected(true);
      myRbInlineThisOnly.setSelected(false);
    }
    getPreviewAction().setEnabled(myRbInlineAll.isSelected());
    myRbInlineAll.addItemListener(
      new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          boolean enabled = myRbInlineAll.isSelected();
          getPreviewAction().setEnabled(enabled);
        }
      }
    );

    return optionsPanel;
  }

  protected void doAction() {
    invokeRefactoring(new InlineMethodProcessor(getProject(), myMethod, myReferenceElement, myEditor, isInlineThisOnly()));
    RefactoringSettings settings = RefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_METHOD_THIS = isInlineThisOnly();
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_METHOD);
  }
}