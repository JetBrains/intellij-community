
package com.intellij.find.findUsages;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.search.SearchScopeCache;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;

public class FindThrowUsagesDialog extends FindUsagesDialog {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbStrict;
  private JComboBox myCbExns;
  private boolean myHasFindWhatPanel;
  private ThrowSearchUtil.Root [] myRoots;

  public FindThrowUsagesDialog(final PsiElement element, 
                               final Project project,
                               final FindUsagesOptions findUsagesOptions, 
                               final boolean toShowInNewTab,
                               final boolean isShowInNewTabEnabled, boolean isSingleFile
                               ){
    super(element, project, findUsagesOptions, toShowInNewTab, isShowInNewTabEnabled, isSingleFile);
  }

  protected void init () {
    // Kludge: myRoots used in super.init, which caller from constructor
    myRoots = ThrowSearchUtil.getSearchRoots(myPsiElement);
    super.init ();
  }

  public JComponent getPreferredFocusedControl() {
    return myHasFindWhatPanel ? myCbUsages : null;
  }

  public FindUsagesOptions getShownOptions(){
    final FindUsagesOptions options = new FindUsagesOptions(myProject, SearchScopeCache.getInstance(myProject));
    options.clear();
    options.isUsages = true;
    options.isStrictThrowUsages = false;
    return options;
  }

  protected JComponent createNorthPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    myCbExns = new JComboBox (myRoots);
    panel.add(myCbExns, gbConstraints);

    return panel;
  }

  public void calcFindUsagesOptions(final FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);
    options.isUsages = isSelected(myCbUsages) || !myHasFindWhatPanel;
    options.isStrictThrowUsages = isSelected(myCbStrict);
    options.isThrowUsages = true;
  }

  protected JPanel createFindWhatPanel() {
    final JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder("Find"));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel("Usages"       , myFindUsagesOptions.isUsages,            findWhatPanel,  true, 'U');
    myCbStrict = addCheckboxToPanel("Strict search", myFindUsagesOptions.isStrictThrowUsages, findWhatPanel,  true, 'S');
    //final ThrowSearchUtil.Root[] searchRoots = ThrowSearchUtil.getSearchRoots(getPsiElement ());

    //final PsiThrowStatement throwStatement = (PsiThrowStatement)getPsiElement();
    //final boolean exactExnType = ThrowSearchUtil.isExactExnType(throwStatement.getException ());
    //if (exactExnType) {
    //  myCbStrict.setEnabled(false);
    //}
    myHasFindWhatPanel = true;
    return findWhatPanel;
  }

  protected void doOKAction() {
    myFindUsagesOptions.myThrowRoot = (ThrowSearchUtil.Root)myCbExns.getSelectedItem();
    super.doOKAction();
  }

  protected void update(){
    if (!myHasFindWhatPanel){
      setOKActionEnabled(true);
      return;
    }
    else{
      myFindUsagesOptions.myThrowRoot = (ThrowSearchUtil.Root)myCbExns.getSelectedItem();
      final boolean hasSelected = isSelected(myCbUsages);
      setOKActionEnabled(hasSelected);
    }
  }
}