package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

class IntroduceConstantDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceConstantDialog");
  @NonNls private static final String RECENTS_KEY = "IntroduceConstantDialog.RECENTS_KEY";
  @NonNls private static final String NONNLS_SELECTED_PROPERTY = "INTRODUCE_CONSTANT_NONNLS";

  private Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myInvokedOnDeclaration;
  private final PsiExpression[] myOccurrences;
  private final int myOccurrencesCount;
  private PsiClass myTargetClass;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;

  private JRadioButton myRbPrivate;
  private JRadioButton myRbProtected;
  private JRadioButton myRbpackageLocal;
  private JRadioButton myRbPublic;

  private TypeSelector myTypeSelector;
  private StateRestoringCheckBox myCbDeleteVariable;
  private final CodeStyleManager myCodeStyleManager;
  private TextAccessor myTfTargetClassName;
  private PsiClass myDestinationClass;
  private JPanel myTypePanel;
  private JPanel myTargetClassNamePanel;
  private JPanel myPanel;
  private JLabel myTypeLabel;
  private JPanel myNameSuggestionPanel;
  private JLabel myNameSuggestionLabel;
  private JLabel myTargetClassNameLabel;
  private JCheckBox myCbNonNls;

  public IntroduceConstantDialog(Project project,
                                 PsiClass parentClass,
                                 PsiExpression initializerExpression,
                                 PsiLocalVariable localVariable,
                                 boolean isInvokedOnDeclaration,
                                 PsiExpression[] occurrences,
                                 PsiClass targetClass,
                                 TypeSelectorManager typeSelectorManager) {
    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrences = occurrences;
    myOccurrencesCount = occurrences.length;
    myTargetClass = targetClass;
    myTypeSelectorManager = typeSelectorManager;
    myDestinationClass = null;

    setTitle(IntroduceConstantHandler.REFACTORING_NAME);
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
    init();

    final String ourLastVisibility = RefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY;
    if (PsiModifier.PUBLIC.equals(ourLastVisibility)) {
      myRbPublic.setSelected(true);
    } else if (PsiModifier.PROTECTED.equals(ourLastVisibility)) {
      myRbProtected.setSelected(true);
    } else if (PsiModifier.PACKAGE_LOCAL.equals(ourLastVisibility)) {
      myRbpackageLocal.setSelected(true);
    } else if (PsiModifier.PRIVATE.equals(ourLastVisibility)) {
      myRbPrivate.setSelected(true);
    } else {
      myRbPrivate.setSelected(true);
    }

    updateVisibilityPanel();
  }

  public String getEnteredName() {
    return myNameField.getName();
  }

  private String getTargetClassName() {
    return myTfTargetClassName.getText().trim();
  }

  public PsiClass getDestinationClass () {
    return myDestinationClass;
  }

  public String getFieldVisibility() {
    if (myRbPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myRbpackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (myRbProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    if (myRbPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    }
    LOG.assertTrue(false);
    return null;
  }

  public boolean isReplaceAllOccurrences() {
    return myOccurrencesCount > 1 && myCbReplaceAll.isSelected();
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_CONSTANT);
  }

  protected JComponent createNorthPanel() {
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    myTypePanel.setLayout(new BorderLayout());
    myTypePanel.add(myTypeSelector.getComponent(), BorderLayout.CENTER);
    if (myTypeSelector.getFocusableComponent() != null) {
      myTypeLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    myNameField = new NameSuggestionsField(myProject);
    myNameSuggestionPanel.setLayout(new BorderLayout());

    myNameSuggestionPanel.add(myNameField.getComponent(), BorderLayout.CENTER);
    myNameSuggestionLabel.setLabelFor(myNameField.getFocusableComponent());

    Set<String> possibleClassNames = new LinkedHashSet<String>();
    for (final PsiExpression occurrence : myOccurrences) {
      final PsiClass parentClass = new IntroduceConstantHandler().getParentClass(occurrence);
      if (parentClass != null && parentClass.getQualifiedName() != null) {
        possibleClassNames.add(parentClass.getQualifiedName());
      }
    }
    if (possibleClassNames.size() > 1) {
      ReferenceEditorComboWithBrowseButton targetClassName =
        new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), "", PsiManager.getInstance(myProject), true, RECENTS_KEY);
      myTargetClassNamePanel.setLayout(new BorderLayout());
      myTargetClassNamePanel.add(targetClassName, BorderLayout.CENTER);
      myTargetClassNameLabel.setLabelFor(targetClassName);
      targetClassName.setHistory(possibleClassNames.toArray(new String[possibleClassNames.size()]));
      myTfTargetClassName = targetClassName;
      targetClassName.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          targetClassChanged();
        }
      });
    }
    else {
      ReferenceEditorWithBrowseButton targetClassName =
        new ReferenceEditorWithBrowseButton(new ChooseClassAction(), "", PsiManager.getInstance(myProject), true);
      myTargetClassNamePanel.setLayout(new BorderLayout());
      myTargetClassNamePanel.add(targetClassName, BorderLayout.CENTER);
      myTargetClassNameLabel.setLabelFor(targetClassName);
      myTfTargetClassName = targetClassName;
      targetClassName.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          targetClassChanged();
        }
      });
    }

    final String propertyName;
    if (myLocalVariable != null) {
      propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE);
    }
    else {
      propertyName = null;
    }
    final NameSuggestionsManager nameSuggestionsManager =
      new NameSuggestionsManager(myTypeSelector, myNameField, new NameSuggestionsGenerator() {
        public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
          return myCodeStyleManager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, propertyName, myInitializerExpression, type);
        }

        public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type) {
          LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
          LookupItemPreferencePolicy policy =
            CompletionUtil.completeVariableNameForRefactoring(myProject, set, prefix, type, VariableKind.STATIC_FINAL_FIELD);
          return new Pair<LookupItemPreferencePolicy, Set<LookupItem>>(policy, set);
        }
      }, myProject);

    nameSuggestionsManager.setLabelsFor(myTypeLabel, myNameSuggestionLabel);
    //////////
    if (myOccurrencesCount > 1) {
      myCbReplaceAll.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateTypeSelector();

          myNameField.requestFocusInWindow();
        }
      });
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurences", myOccurrencesCount));
    }
    else {
      myCbReplaceAll.setVisible(false);
    }

    if (myLocalVariable != null) {
      if (myInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      }
      else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
          new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            updateCbDeleteVariable();
          }
        });
      }
    }
    else {
      myCbDeleteVariable.setVisible(false);
    }

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (myTypeSelectorManager.isSuggestedType("java.lang.String") &&
        psiManager.getEffectiveLanguageLevel().hasEnumKeywordAndAutoboxing() &&
        psiManager.findClass(AnnotationUtil.NON_NLS, myParentClass.getResolveScope()) != null) {
      final PropertiesComponent component = PropertiesComponent.getInstance(myProject);
      myCbNonNls.setSelected(component.isTrueValue(NONNLS_SELECTED_PROPERTY));
      myCbNonNls.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          component.setValue(NONNLS_SELECTED_PROPERTY, Boolean.toString(myCbNonNls.isSelected()));
        }
      });
    } else {
      myCbNonNls.setVisible(false);
    }

    updateTypeSelector();

    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbPrivate);
    bg.add(myRbpackageLocal);
    bg.add(myRbProtected);
    bg.add(myRbPublic);

    return myPanel;
  }

  private void targetClassChanged() {
    final String targetClassName = getTargetClassName();
    myTargetClass = PsiManager.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
    updateVisibilityPanel();
  }

  protected JComponent createCenterPanel() {
    return new JPanel();
  }

  public boolean isDeleteVariable() {
    return myInvokedOnDeclaration || myCbDeleteVariable != null && myCbDeleteVariable.isSelected();
  }

  public boolean isAnnotateAsNonNls() {
    return myCbNonNls != null && myCbNonNls.isSelected();
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    }
    else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    }
    else {
      myTypeSelectorManager.setAllOccurences(false);
    }
    Window dialog = (Window)SwingUtilities.getAncestorOfClass(Window.class, myPanel);
    if (dialog != null) {
      dialog.pack();
    }
    myPanel.revalidate();
  }

  private void updateVisibilityPanel() {
    if (myTargetClass == null) return;
    if (myTargetClass.isInterface()) {
      myRbPrivate.setEnabled(false);
      myRbProtected.setEnabled(false);
      myRbpackageLocal.setEnabled(false);
      myRbPublic.setEnabled(true);
      myRbPublic.setSelected(true);
    }
    else {
      myRbPrivate.setEnabled(true);
      myRbProtected.setEnabled(true);
      myRbpackageLocal.setEnabled(true);
      myRbPublic.setEnabled(true);
      // exclude all modifiers not visible from all occurences
      final Set<String> visible = new THashSet<String>();
      visible.add(PsiModifier.PRIVATE);
      visible.add(PsiModifier.PROTECTED);
      visible.add(PsiModifier.PACKAGE_LOCAL);
      visible.add(PsiModifier.PUBLIC);
      for (PsiExpression occurrence : myOccurrences) {
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (Iterator<String> iterator = visible.iterator(); iterator.hasNext();) {
          String modifier = iterator.next();

          try {
            final String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier;
            final PsiField field = psiManager.getElementFactory().createFieldFromText(modifierText + " int xxx;", myTargetClass);
            if (!ResolveUtil.isAccessible(field, myTargetClass, field.getModifierList(), occurrence, myTargetClass, null)) {
              iterator.remove();
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      if (visible.contains(PsiModifier.PUBLIC)) myRbPublic.setSelected(true);
      if (visible.contains(PsiModifier.PACKAGE_LOCAL)) myRbpackageLocal.setSelected(true);
      if (visible.contains(PsiModifier.PROTECTED)) myRbProtected.setSelected(true);
      if (visible.contains(PsiModifier.PRIVATE)) myRbPrivate.setSelected(true);
    }
  }

  protected void doOKAction() {
    final String targetClassName = getTargetClassName();
    PsiClass newClass = myParentClass;

    if (!"".equals (targetClassName)) {
      final PsiManager manager = PsiManager.getInstance(myProject);
      newClass = manager.findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
      if (newClass == null) {
        CommonRefactoringUtil.showErrorMessage(
                IntroduceConstantHandler.REFACTORING_NAME,
                RefactoringBundle.message("class.does.not.exist.in.the.project"),
                HelpID.INTRODUCE_FIELD,
                myProject);
        return;
      }
      myDestinationClass = newClass;
    }

    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    } else if (!PsiManager.getInstance(myProject).getNameHelper().isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
              IntroduceFieldHandler.REFACTORING_NAME,
              errorString,
              HelpID.INTRODUCE_FIELD,
              myProject);
      return;
    }
    PsiField oldField = newClass.findFieldByName(fieldName, true);

    if (oldField != null) {
      int answer = Messages.showYesNoDialog(
              myProject,
              RefactoringBundle.message("field.exists", fieldName, oldField.getContainingClass().getQualifiedName()),
              IntroduceFieldHandler.REFACTORING_NAME,
              Messages.getWarningIcon()
      );
      if (answer != 0) {
        return;
      }
    }

    RefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getFieldVisibility();

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, targetClassName);
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getComponent();
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(RefactoringBundle.message("choose.destination.class"), GlobalSearchScope.projectScope(myProject), new TreeClassChooser.ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      if (myTargetClass != null) {
        chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
      }
      chooser.showDialog();
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }
}
