package com.intellij.refactoring.rename;

import com.intellij.ant.PsiAntElement;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class RenameDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameDialog");
  private SuggestedNameInfo mySuggestedNameInfo;

  public static interface Callback {
    void run(RenameDialog dialog);
  }

  private JLabel myNameLabel;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchInNonJavaFiles;
  private JCheckBox myCbRenameVariables;
  private JCheckBox myCbRenameInheritors;
  private JCheckBox myCbRenameBoundForms;
  private JLabel myNewNamePrefix = new JLabel("");


  private String myHelpID;
  private Project myProject;
  private PsiElement myPsiElement;
  private final PsiElement myNameSuggestionContext;
  private Callback myCallback;


  public RenameDialog(Project project, PsiElement psiElement,
                      PsiElement nameSuggestionContext, String helpID, Callback callback) {
    super(project, true);
    myProject = project;
    myPsiElement = psiElement;
    myNameSuggestionContext = nameSuggestionContext;
    myCallback = callback;
    setTitle("Rename");

    createNewNameComponent();
    init();

    myNameLabel.setText("Rename " + getFullName() + " and its usages to:");
    boolean toSearchInComments =
      RefactoringSettings.getInstance().isToSearchInCommentsForRename(myPsiElement);
    boolean toSearchInNonJava =
      RefactoringSettings.getInstance().isToSearchInNonJavaFilesForRename(myPsiElement);
    myCbSearchInComments.setSelected(toSearchInComments);

    if (myCbSearchInNonJavaFiles.isEnabled()) {
      myCbSearchInNonJavaFiles.setSelected(toSearchInNonJava);
    }

    if (!showSearchCheckboxes(myPsiElement)) {
      myCbSearchInComments.setVisible(false);
      myCbSearchInNonJavaFiles.setVisible(false);
    }

    validateButtons();
    myHelpID = helpID;
  }

  private String getFullName() {
    return (UsageViewUtil.getType(myPsiElement) + " " + UsageViewUtil.getDescriptiveName(myPsiElement)).trim();
  }

  private boolean showSearchCheckboxes(PsiElement e) {
    if (e instanceof PsiAntElement) return false;

    return true;
  }

  private void createNewNameComponent() {
    String[] suggestedNames = getSuggestedNames();
    myNameSuggestionsField = new NameSuggestionsField(suggestedNames, myProject, myPsiElement instanceof PsiAntElement ? StdFileTypes.PLAIN_TEXT : StdFileTypes.JAVA);
    myNameSuggestionsField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    });

    if (myPsiElement instanceof PsiVariable) {
      myNameSuggestionsField.getComponent().registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          completeVariable(myNameSuggestionsField.getEditor());
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
  }

  private void completeVariable(Editor editor) {
    String prefix = myNameSuggestionsField.getName();
    PsiVariable var = (PsiVariable)myPsiElement;
    VariableKind kind = CodeStyleManager.getInstance(myProject).getVariableKind(var);
    LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
    CompletionUtil.completeVariableName(myProject, set, prefix, var.getType(), kind);

    if (prefix.length() == 0) {
      String[] suggestedNames = getSuggestedNames();
      for (int i = 0; i < suggestedNames.length; i++) {
        LookupItemUtil.addLookupItem(set, suggestedNames[i], "");
      }
    }

    LookupItem[] lookupItems = set.toArray(new LookupItem[set.size()]);
    editor.getCaretModel().moveToOffset(prefix.length());
    editor.getSelectionModel().removeSelection();
    LookupManager.getInstance(myProject).showLookup(editor, lookupItems, prefix, null, new CharFilter() {
      public int accept(char c) {
        if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
    });
  }

  private String[] getSuggestedNames() {
    String initialName = UsageViewUtil.getShortName(myPsiElement);
    mySuggestedNameInfo = suggestNamesForElement(myPsiElement);

    String parameterName = null;
    if (myNameSuggestionContext != null) {
      final PsiElement nameSuggestionContextParent = myNameSuggestionContext.getParent();
      if (nameSuggestionContextParent.getParent() instanceof PsiExpressionList) {
        final PsiExpressionList expressionList = (PsiExpressionList)nameSuggestionContextParent.getParent();
        final PsiElement parent = expressionList.getParent();
        if (parent instanceof PsiCallExpression) {
          final PsiMethod method = ((PsiCallExpression)parent).resolveMethod();
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            final PsiExpression[] expressions = expressionList.getExpressions();
            for (int i = 0; i < expressions.length; i++) {
              PsiExpression expression = expressions[i];
              if (expression == nameSuggestionContextParent) {
                if (i < parameters.length) {
                  parameterName = parameters[i].getName();
                }
                break;
              }
            }
          }
        }
      }
    }
    final String[] strings = mySuggestedNameInfo != null ? mySuggestedNameInfo.names : ArrayUtil.EMPTY_STRING_ARRAY;
    ArrayList<String> list = new ArrayList<String>(Arrays.asList(strings));
    final String properlyCased = suggestProperlyCasedName(myPsiElement);
    if (!list.contains(initialName)) {
      list.add(0, initialName);
    }
    else {
      int i = list.indexOf(initialName);
      list.remove(i);
      list.add(0, initialName);
    }
    if (properlyCased != null && !properlyCased.equals(initialName)) {
      list.add(1, properlyCased);
    }
    if (parameterName != null && !list.contains(parameterName)) {
      list.add(parameterName);
    }
    ContainerUtil.removeDuplicates(list);
    return (String[])list.toArray(new String[list.size()]);
  }

  private String suggestProperlyCasedName(PsiElement psiElement) {
    if (!(psiElement instanceof PsiNamedElement)) return null;
    String name = ((PsiNamedElement)psiElement).getName();
    if (psiElement instanceof PsiVariable) {
      final CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx)CodeStyleManager.getInstance(myProject);
      final VariableKind kind = codeStyleManager.getVariableKind((PsiVariable)psiElement);
      final String prefix = codeStyleManager.getPrefixByVariableKind(kind);
      if (name.startsWith(prefix)) {
        name = name.substring(prefix.length());
      }
      final String[] words = PsiNameHelper.splitNameIntoWords(name);
      if (VariableKind.STATIC_FINAL_FIELD.equals(kind)) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < words.length; i++) {
          String word = words[i];
          if (i > 0) buffer.append('_');
          buffer.append(word.toUpperCase());
        }
        return buffer.toString();
      }
      else {
        StringBuffer buffer = new StringBuffer(prefix);
        for (int i = 0; i < words.length; i++) {
          String word = words[i];
          final boolean prefixRequiresCapitalization = prefix.length() > 0 && !StringUtil.endsWithChar(prefix, '_');
          if (i > 0 || prefixRequiresCapitalization) {
            buffer.append(StringUtil.capitalize(word));
          }
          else {
            buffer.append(StringUtil.decapitalize(word));
          }
        }
        return buffer.toString();
      }

    }
    return name;
  }

  private SuggestedNameInfo suggestNamesForElement(final PsiElement element) {
    PsiVariable var = null;
    if (element instanceof PsiVariable) {
      var = (PsiVariable)element;
    }
    else if (element instanceof PsiIdentifier) {
      PsiIdentifier identifier = (PsiIdentifier)element;
      if (identifier.getParent() instanceof PsiVariable) {
        var = (PsiVariable)identifier.getParent();
      }
    }

    if (var == null) return null;

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
    VariableKind variableKind = codeStyleManager.getVariableKind(var);
    return codeStyleManager.suggestVariableName(variableKind, null, var.getInitializer(), var.getType());
  }

  public String getNewName() {
    return myNameSuggestionsField.getName().trim();
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchInNonJavaFiles.isSelected();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField.getComponent();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  public boolean shouldRenameVariables() {
    return myCbRenameVariables != null ? myCbRenameVariables.isSelected() : false;
  }

  public boolean shouldRenameInheritors() {
    return myCbRenameInheritors != null ? myCbRenameInheritors.isSelected() : false;
  }

  public boolean shouldRenameForms() {
    return myCbRenameBoundForms != null ? myCbRenameBoundForms.isSelected() : false;
  }


  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myNameLabel = new JLabel();
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, ("".equals(myNewNamePrefix.getText()) ? 0 : 1));
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myNewNamePrefix, gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 0;
    panel.add(myNameSuggestionsField.getComponent(), gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInComments = new NonFocusableCheckBox("Search in comments and strings");
    myCbSearchInComments.setMnemonic('S');
    myCbSearchInComments.setSelected(true);
    panel.add(myCbSearchInComments, gbConstraints);

    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInNonJavaFiles = new NonFocusableCheckBox("Search in non-java files");
    myCbSearchInNonJavaFiles.setMnemonic('e');
    myCbSearchInNonJavaFiles.setSelected(true);
    panel.add(myCbSearchInNonJavaFiles, gbConstraints);
    if (!RefactoringUtil.isSearchInNonJavaEnabled(myPsiElement)) {
      myCbSearchInNonJavaFiles.setEnabled(false);
      myCbSearchInNonJavaFiles.setSelected(false);
      myCbSearchInNonJavaFiles.setVisible(false);
    }

    if (myPsiElement instanceof PsiClass) {
      gbConstraints.insets = new Insets(4, 8, 4, 8);
      gbConstraints.gridwidth = 1;
      gbConstraints.gridx = 0;
      gbConstraints.weightx = 1;
      gbConstraints.fill = GridBagConstraints.BOTH;
      myCbRenameVariables = new NonFocusableCheckBox("Rename variables");
      myCbRenameVariables.setMnemonic('v');
      myCbRenameVariables.setSelected(true);
      panel.add(myCbRenameVariables, gbConstraints);

      gbConstraints.insets = new Insets(4, 4, 4, 8);
      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gbConstraints.gridx = 1;
      gbConstraints.weightx = 1;
      gbConstraints.fill = GridBagConstraints.BOTH;
      myCbRenameInheritors = new NonFocusableCheckBox("Rename inheritors");
      myCbRenameInheritors.setMnemonic('i');
      myCbRenameInheritors.setSelected(true);
      panel.add(myCbRenameInheritors, gbConstraints);

      String qName = ((PsiClass)myPsiElement).getQualifiedName();
      if (qName != null) {
        PsiFile[] forms = myPsiElement.getManager().getSearchHelper().findFormsBoundToClass(qName);
        if (forms.length > 0) {
          gbConstraints.insets = new Insets(4, 8, 4, 8);
          gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
          gbConstraints.gridx = 0;
          gbConstraints.weightx = 1;
          gbConstraints.fill = GridBagConstraints.BOTH;
          myCbRenameBoundForms = new NonFocusableCheckBox("Rename bound forms");
          myCbRenameBoundForms.setMnemonic('f');
          myCbRenameBoundForms.setSelected(true);
          panel.add(myCbRenameBoundForms, gbConstraints);
        }
      }
    }

    return panel;
  }

  /*
  protected JComponent createSouthPanel() {
    myCbPreviewResults = new NonFocusableCheckBox("Preview usages to be changed");
    myCbPreviewResults.setSelected(RefactoringSettings.getInstance().RENAME_PREVIEW_USAGES);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
    myCbPreviewResults.setMnemonic('P');
    panel.add(myCbPreviewResults, BorderLayout.WEST);
    return panel;
  }
  */

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  protected void doAction() {
    LOG.assertTrue(myPsiElement.isValid());

    final RefactoringSettings settings = RefactoringSettings.getInstance();
    if (!checkNameConflicts()) {
      myNameSuggestionsField.requestFocusInWindow();
      return;
    }

    settings.setToSearchInCommentsForRename(myPsiElement, isSearchInComments());
    if (myCbSearchInNonJavaFiles.isEnabled()) {
      settings.setToSearchInNonJavaFilesForRename(myPsiElement, isSearchInNonJavaFiles());
    }
    if (mySuggestedNameInfo != null) {
      mySuggestedNameInfo.nameChoosen(getNewName());
    }

    myCallback.run(this);
  }

  private boolean checkNameConflicts() {
    final PsiManager manager = PsiManager.getInstance(myProject);
    String newName = getNewName();
    try {
      PsiElementFactory factory = manager.getElementFactory();
      if (myPsiElement instanceof PsiMethod) {
        PsiMethod refactoredMethod = (PsiMethod)myPsiElement;
        if (newName.equals(refactoredMethod.getName())) {
          return true;
        }
        PsiMethod prototype = (PsiMethod)refactoredMethod.copy();
        prototype.getNameIdentifier().replace(factory.createIdentifier(newName));
        PsiClass aClass = refactoredMethod.getContainingClass();
        return RefactoringMessageUtil.checkMethodConflicts(
          aClass != null ? (PsiElement)aClass : (PsiElement)refactoredMethod.getContainingFile(),
          refactoredMethod,
          prototype
        );
      }
      else if (myPsiElement instanceof PsiField) {
        PsiField refactoredField = (PsiField)myPsiElement;
        if (newName.equals(refactoredField.getName())) {
          return true;
        }
        PsiClass aClass = refactoredField.getContainingClass();
        return RefactoringMessageUtil.checkFieldConflicts(
          aClass != null ? (PsiElement)aClass : (PsiElement)refactoredField.getContainingFile(),
          newName
        );
      }
      else if (myPsiElement instanceof PsiClass) {
        final PsiClass aClass = ((PsiClass)myPsiElement);
        if (newName.equals(aClass.getName())) {
          return true;
        }
        if (myPsiElement.getParent() instanceof PsiClass) { // innerClass
          PsiClass containingClass = (PsiClass)myPsiElement.getParent();
          PsiClass[] innerClasses = containingClass.getInnerClasses();
          for (int idx = 0; idx < innerClasses.length; idx++) {
            PsiClass innerClass = innerClasses[idx];
            if (newName.equals(innerClass.getName())) {
              int ret = Messages.showYesNoDialog(
                myProject,
                "Inner class " + newName + " is already defined in class " + containingClass.getQualifiedName() +
                ".\nContinue anyway?",
                "Warning",
                Messages.getWarningIcon()
              );
              if (ret != 0) {
                return false;
              }
              break;
            }
          }
        }
        else {
          final String qualifiedNameAfterRename = RenameUtil.getQualifiedNameAfterRename(aClass, newName);
          final PsiClass conflictingClass = PsiManager.getInstance(myProject).findClass(qualifiedNameAfterRename);
          if (conflictingClass != null) {
            RefactoringMessageUtil.showErrorMessage("Error", "Class " + qualifiedNameAfterRename + " already exists",
                                                    null, myProject);
            return false;
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return true;
  }


  protected boolean areButtonsValid() {
    final String newName = getNewName();
    final boolean enabled;
    if (newName == null) {
      return false;
    }
    else {
      if (myPsiElement instanceof PsiAntElement) {
        return newName.trim().matches("[\\d\\w\\_\\.\\-]*");
      }
      else {
        return PsiManager.getInstance(myProject).getNameHelper().isIdentifier(newName.trim());
      }
    }
  }
}
