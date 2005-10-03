
package com.intellij.find.findUsages;

import com.intellij.find.FindSettings;
import com.intellij.find.FindBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class FindUsagesDialog extends DialogWrapper{
  protected final Project myProject;
  protected final PsiElement myPsiElement;
  protected final FindUsagesOptions myFindUsagesOptions;

  private final boolean myToShowInNewTab;
  private final boolean myIsShowInNewTabEnabled;
  private final boolean myIsShowInNewTabVisible;

  private final boolean mySearchForTextOccurencesAvailable;

  private final boolean mySearchInLibrariesAvailable;

  private JCheckBox myCbToOpenInNewTab;

  protected StateRestoringCheckBox myCbToSearchForTextOccurences;
  protected JCheckBox myCbToSkipResultsWhenOneUsage;

  private ActionListener myUpdateAction;

  protected StateRestoringCheckBox myCbIncludeOverloadedMethods;
  private boolean myIncludeOverloadedMethodsAvailable = false;
  private ScopeChooserCombo myScopeCombo;

  protected FindUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions,
                          boolean toShowInNewTab, boolean isShowInNewTabEnabled, boolean isSingleFile){
    super(project, true);
    myProject = project;
    myPsiElement = element;
    myFindUsagesOptions = findUsagesOptions;
    myToShowInNewTab = toShowInNewTab;
    myIsShowInNewTabEnabled = isShowInNewTabEnabled;
    myIsShowInNewTabVisible = !isSingleFile;

    if (!isSingleFile){
      if (myPsiElement instanceof PsiClass){
        mySearchForTextOccurencesAvailable = ((PsiClass)myPsiElement).getQualifiedName() != null;
      }
      else if (myPsiElement instanceof PsiPackage){
        mySearchForTextOccurencesAvailable = true;
      }
      else{
        mySearchForTextOccurencesAvailable = false;
      }
    }
    else{
      mySearchForTextOccurencesAvailable = false;
    }

    if (!isSingleFile){
      mySearchInLibrariesAvailable = !myPsiElement.getManager().isInProject(myPsiElement);
    }
    else{
      mySearchInLibrariesAvailable = false;
    }

    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      myIncludeOverloadedMethodsAvailable = MethodSignatureUtil.hasOverloads (method);
    }

    myUpdateAction = new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        update();
      }
    };

    setButtonsMargin(null);
    init();
    setOKButtonText(FindBundle.message("find.dialog.find.button"));
    setOKButtonIcon(IconLoader.getIcon("/actions/find.png"));
    setTitle(isSingleFile ? FindBundle.message("find.usages.in.file.dialog.title") : FindBundle.message("find.usages.dialog.title"));
    update();
  }

  public abstract FindUsagesOptions getShownOptions();

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected boolean isInFileOnly() {
    return !myIsShowInNewTabVisible || myPsiElement.getManager().getSearchHelper().getUseScope(myPsiElement) instanceof LocalSearchScope;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    final JLabel promptLabel = new JLabel(getLabelText());
    panel.add(promptLabel, gbConstraints);

    return panel;
  }

  protected String getLabelText() {
    return UsageViewUtil.capitalize(UsageViewUtil.getType(myPsiElement)) + " " + UsageViewUtil.getDescriptiveName(myPsiElement);
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    panel.add(_panel, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    if (myIsShowInNewTabVisible){
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      myCbToOpenInNewTab.setSelected(myToShowInNewTab);
      myCbToOpenInNewTab.setEnabled(myIsShowInNewTabEnabled);
      _panel.add(myCbToOpenInNewTab, BorderLayout.EAST);
    }

    JPanel allOptionsPanel = createAllOptionsPanel();
    if(allOptionsPanel != null){
      panel.add(allOptionsPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    }
    return panel;
  }

  protected final PsiElement getPsiElement() {
    return myPsiElement;
  }

  public final FindUsagesOptions calcFindUsagesOptions() {
    calcFindUsagesOptions(myFindUsagesOptions);
    return myFindUsagesOptions;
  }

  public void calcFindUsagesOptions(FindUsagesOptions options){
    if (myScopeCombo != null) {
      options.searchScope = myScopeCombo.getSelectedScope();
    }
    else {
      options.searchScope = GlobalSearchScope.allScope(myProject);
    }

    if (isToChange(myCbToSearchForTextOccurences)) {
      options.isSearchForTextOccurences = isSelected(myCbToSearchForTextOccurences);
    }
    else {
      options.isSearchForTextOccurences = false;
    }

    if (myIncludeOverloadedMethodsAvailable && isToChange(myCbIncludeOverloadedMethods)) {
      options.isIncludeOverloadUsages = myCbIncludeOverloadedMethods.isSelected();
    }
    else {
      options.isIncludeOverloadUsages = false;
    }
  }

  protected abstract void update();

  public boolean isShowInSeparateWindow() {
    if (myCbToOpenInNewTab == null){
      return false;
    }
    return myCbToOpenInNewTab.isSelected();
  }

  public boolean isSkipResultsWhenOneUsage(){
    return myCbToSkipResultsWhenOneUsage != null && myCbToSkipResultsWhenOneUsage.isSelected();
  }

  protected void doOKAction() {
    if (myScopeCombo != null && myScopeCombo.getSelectedScope() == null) return;

    FindSettings settings = FindSettings.getInstance();

    if (myScopeCombo != null) {
      settings.setDefaultScopeName(myScopeCombo.getSelectedScopeName());
    }
    if (mySearchForTextOccurencesAvailable && myCbToSearchForTextOccurences != null && myCbToSearchForTextOccurences.isEnabled()){
      settings.setSearchForTextOccurences(myCbToSearchForTextOccurences.isSelected());
    }

    if (myIncludeOverloadedMethodsAvailable) {
      settings.setSearchOverloadedMethods(myCbIncludeOverloadedMethods.isSelected());
    }
    if (myCbToSkipResultsWhenOneUsage != null){
      settings.setSkipResultsWithOneUsage(isSkipResultsWhenOneUsage());
    }

    super.doOKAction();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(FindUsagesManager.getHelpID(myPsiElement));
  }

  protected static boolean isToChange(JCheckBox cb) {
    if (cb == null){
      return false;
    }
    return cb.getParent() != null;
  }

  protected static boolean isSelected(JCheckBox cb) {
    if (cb == null){
      return false;
    }
    if (cb.getParent() == null) {
      return false;
    }
    return cb.isSelected();
  }

  protected StateRestoringCheckBox addCheckboxToPanel(String name, boolean toSelect, JPanel panel, boolean toUpdate) {
    StateRestoringCheckBox cb = new StateRestoringCheckBox(name);
    cb.setSelected(toSelect);
    panel.add(cb);
    if (toUpdate){
      cb.addActionListener(myUpdateAction);
    }
    return cb;
  }

  protected JPanel createAllOptionsPanel(){
    JPanel allOptionsPanel = new JPanel();

    JPanel findWhatPanel = createFindWhatPanel();
    JPanel usagesOptionsPanel = createUsagesOptionsPanel();
    int grids = 0;
    if(findWhatPanel != null){
      grids++;
    }
    if(usagesOptionsPanel != null){
      grids++;
    }
    if(grids != 0){
      allOptionsPanel.setLayout(new GridLayout(1, grids, 8, 0));
      if(findWhatPanel != null){
        allOptionsPanel.add(findWhatPanel);
      }
      if(usagesOptionsPanel != null){
        allOptionsPanel.add(usagesOptionsPanel);
      }
    }

    JComponent scopePanel = createSearchScopePanel();
    if (scopePanel != null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(allOptionsPanel, BorderLayout.CENTER);
      panel.add(scopePanel, BorderLayout.SOUTH);
      return panel;
    }

    return allOptionsPanel;
  }

  protected abstract JPanel createFindWhatPanel();

  protected JPanel createUsagesOptionsPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.options.group")));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

    boolean isEmpty = true;

    if(mySearchForTextOccurencesAvailable){
      myCbToSearchForTextOccurences = addCheckboxToPanel(FindBundle.message("find.options.search.for.text.occurences.checkbox"), FindSettings.getInstance().isSearchForTextOccurences(), optionsPanel, false);
      isEmpty = false;
    }

    if (myIsShowInNewTabVisible){
      myCbToSkipResultsWhenOneUsage = addCheckboxToPanel(FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox"), FindSettings.getInstance().isSkipResultsWithOneUsage(), optionsPanel, false);
      isEmpty = false;
    }

    if (myIncludeOverloadedMethodsAvailable){
      myCbIncludeOverloadedMethods = addCheckboxToPanel(FindBundle.message("find.options.include.overloaded.methods.checkbox"), FindSettings.getInstance().isSearchOverloadedMethods(), optionsPanel, false);
      isEmpty = false;
    }


    if(isEmpty){
      return null;
    }
    else{
      return optionsPanel;
    }
  }

  protected JComponent createSearchScopePanel() {
    if (isInFileOnly()) return null;
    JPanel optionsPanel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(FindBundle.message("find.scope.label"));
    optionsPanel.add(label, BorderLayout.WEST);
    myScopeCombo = new ScopeChooserCombo(myProject, mySearchInLibrariesAvailable, true, FindSettings.getInstance().getDefaultScopeName());
    optionsPanel.add(myScopeCombo, BorderLayout.CENTER);
    label.setLabelFor(myScopeCombo.getComboBox());
    return optionsPanel;
  }

  protected abstract JComponent getPreferredFocusedControl();

  public JComponent getPreferredFocusedComponent() {
    if (myScopeCombo != null) {
      return myScopeCombo.getComboBox();
    }
    return getPreferredFocusedControl();
  }
}
