package com.intellij.cyclicDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import javax.swing.text.DefaultFormatterFactory;
import java.awt.event.KeyEvent;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesAction extends AnAction{
  private final String myAnalysisVerb;
  private final String myAnalysisNoon;
  private final AnalysisScope.PsiFileFilter myFileFilter;
  private final String myTitle;

  public CyclicDependenciesAction() {
    myAnalysisVerb = "Analyze";
    myAnalysisNoon = "Analysis";
    myFileFilter = AnalysisScope.SOURCE_JAVA_FILES;
    myTitle = "Cyclic Dependency Analysis";
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(
      getInspectionScope(event.getDataContext()) != null || event.getDataContext().getData(DataConstants.PSI_FILE) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Module module = (Module)dataContext.getData(DataConstants.MODULE);
    if (project != null) {
      PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
      if (psiFile != null && !(psiFile instanceof PsiJavaFile)) {
        return;
      }
      AnalysisScope scope = getInspectionScope(dataContext);
      boolean showChooseScope = true;
      if (scope.getScopeType() == AnalysisScope.MODULES){
        showChooseScope = false;
      }
      ProjectModuleOrPackageDialog dlg = new ProjectModuleOrPackageDialog(dataContext,
                                                        module != null ? ModuleUtil.getModuleNameInReadAction(module) : null,
                                                        showChooseScope);
      dlg.show();
      if (!dlg.isOK()) return;
      if (showChooseScope){
        if (dlg.isProjectScopeSelected()) {
          scope = getProjectScope(dataContext);
        }
        else {
          if (dlg.isModuleScopeSelected()) {
            scope = getModuleScope(dataContext);
          }
        }
      }

      FileDocumentManager.getInstance().saveAllDocuments();

      new CyclicDependenciesHandler(project, scope, dlg.getMaxPerPackageCycleCount()).analyze();
    }
  }


  private AnalysisScope getInspectionScope(final DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  private AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: package, project, module.
    Project projectContext = (Project)dataContext.getData(DataConstantsEx.PROJECT_CONTEXT);
    if (projectContext != null) {
      return new AnalysisScope(projectContext, myFileFilter);
    }

    Module moduleContext = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext, myFileFilter);
    }

    Module [] modulesArray = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray, myFileFilter);
    }

    AnalysisScope packageScope = getPackageScope(dataContext);
    if (packageScope != null){
      return packageScope;
    }

    return getProjectScope(dataContext);
  }

  private AnalysisScope getProjectScope(DataContext dataContext) {
    return new AnalysisScope((Project)dataContext.getData(DataConstants.PROJECT), myFileFilter);
  }

  private AnalysisScope getModuleScope(DataContext dataContext) {
    return new AnalysisScope((Module)dataContext.getData(DataConstants.MODULE), myFileFilter);
  }

  private AnalysisScope getPackageScope(DataContext dataContext){
    PsiElement psiTarget = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (psiTarget instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiTarget;
      if (!psiDirectory.getManager().isInProject(psiDirectory)) return null;
      return new AnalysisScope(psiDirectory, myFileFilter);
    }
    else if (psiTarget instanceof PsiPackage) {
      PsiPackage pack = (PsiPackage)psiTarget;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs == null || dirs.length == 0) return null;
      return new AnalysisScope(pack, myFileFilter);
    }
    return null;
  }

  private class ProjectModuleOrPackageDialog extends DialogWrapper {
    private String myPackageName;
    private String myModuleName;
    private JRadioButton myPackageButton;
    private JRadioButton myProjectButton;
    private JRadioButton myModuleButton;
    private boolean myShowChooseScope;
    private JFormattedTextField myPerPackageCycleCount;

    private JPanel myScopePanel;
    private JPanel myWholePanel;


    public ProjectModuleOrPackageDialog(DataContext dataContext, String moduleName, boolean showChooseScope) {
      super(true);
      myShowChooseScope = showChooseScope;
      myModuleName = moduleName;
      final AnalysisScope scope = getPackageScope(dataContext);
      if (scope != null){
        myPackageName = scope.getDisplayName();
      }
      init();
      setTitle("Specify " + myTitle + " Scope");
      if (scope == null){
        myPackageButton.setVisible(false);
        myProjectButton.setSelected(true);
      } else {
        myPackageButton.setSelected(true);
      }
      if (moduleName == null){
        myModuleButton.setVisible(false);
      }
    }

    protected JComponent createCenterPanel() {
      myScopePanel.setBorder(IdeBorderFactory.createTitledBorder(myAnalysisNoon + " scope"));
      myPackageButton.setText(myAnalysisVerb + StringUtil.decapitalize(myPackageName));
      myPackageButton.setMnemonic(KeyEvent.VK_K);
      myProjectButton.setText(myAnalysisVerb + " the whole project");
      myProjectButton.setMnemonic(KeyEvent.VK_P);
      ButtonGroup group = new ButtonGroup();
      group.add(myProjectButton);
      if (myModuleName != null) {
        myModuleButton.setText(myAnalysisVerb + " module \'" + myModuleName + "\'");
        myModuleButton.setMnemonic(KeyEvent.VK_M);
        group.add(myModuleButton);
      }
      group.add(myPackageButton);
      myPerPackageCycleCount.setFormatterFactory(new DefaultFormatterFactory());
      myPerPackageCycleCount.setText("1");
      if (!myShowChooseScope){
        myScopePanel.setVisible(false);
      }
      return myWholePanel;
    }

    public boolean isProjectScopeSelected() {
      return myProjectButton.isSelected();
    }

    public boolean isModuleScopeSelected() {
      return myModuleButton != null ? myModuleButton.isSelected() : false;
    }

    public int getMaxPerPackageCycleCount(){
      return Integer.parseInt(myPerPackageCycleCount.getText());
    }
  }
}
