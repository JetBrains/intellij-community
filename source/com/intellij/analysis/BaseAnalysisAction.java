package com.intellij.analysis;

import com.intellij.codeInspection.ex.InspectionManagerEx;
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
import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class BaseAnalysisAction extends AnAction {
  protected final String myTitle;
  protected final String myAnalysisVerb;
  protected final String myAnalysisNoon;

  protected BaseAnalysisAction(String title, String analysisVerb, String analysisNoon) {
    myTitle = title;
    myAnalysisVerb = analysisVerb;
    myAnalysisNoon = analysisNoon;
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
      AnalysisScope scope;
      boolean calledFromNonJavaFile = false;
      PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
      if (psiFile != null && !(psiFile instanceof PsiJavaFile)) {
        scope = new AnalysisScope(psiFile);
        calledFromNonJavaFile = true;
      }
      else {
        scope = getInspectionScope(dataContext);
      }
      FileProjectOrModuleDialog dlg = new FileProjectOrModuleDialog(project,
                                                                    scope.getDisplayName(),
                                                                    module != null && scope.getScopeType() != AnalysisScope.MODULE ? ModuleUtil.getModuleNameInReadAction(module) : null,
                                                                    calledFromNonJavaFile,
                                                                    scope.getScopeType() == AnalysisScope.PROJECT);
      dlg.show();
      if (!dlg.isOK()) return;
      if (dlg.isProjectScopeSelected()) {
        scope = getProjectScope(dataContext);
      }
      else {
        if (dlg.isModuleScopeSelected()) {
          scope = getModuleScope(dataContext);
        }
      }
      ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).getUIOptions().ANALYZE_TEST_SOURCES = dlg.isInspectTestSources();
      scope.setIncludeTestSource(dlg.isInspectTestSources());
      FileDocumentManager.getInstance().saveAllDocuments();

      analyze(project, scope);
    }
  }

  protected abstract void analyze(Project project, AnalysisScope scope);

  private AnalysisScope getInspectionScope(final DataContext dataContext) {
    if (dataContext.getData(DataConstants.PROJECT) == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  private AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: file, directory, package, project, module.
    Project projectContext = (Project)dataContext.getData(DataConstantsEx.PROJECT_CONTEXT);
    if (projectContext != null) {
      return new AnalysisScope(projectContext);
    }

    Module moduleContext = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext);
    }

    Module [] modulesArray = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray);
    }
    PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    if (psiFile != null) {
      return psiFile instanceof PsiJavaFile ? new AnalysisScope(psiFile) : null;
    }

    PsiElement psiTarget = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (psiTarget instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiTarget;
      if (!psiDirectory.getManager().isInProject(psiDirectory)) return null;
      return new AnalysisScope(psiDirectory);
    }
    else if (psiTarget instanceof PsiPackage) {
      PsiPackage pack = (PsiPackage)psiTarget;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs == null || dirs.length == 0) return null;
      return new AnalysisScope(pack);
    }
    else if (psiTarget != null) {
      return null;
    }

    return getProjectScope(dataContext);
  }

  private AnalysisScope getProjectScope(DataContext dataContext) {
    return new AnalysisScope((Project)dataContext.getData(DataConstants.PROJECT));
  }

  private AnalysisScope getModuleScope(DataContext dataContext) {
    return new AnalysisScope((Module)dataContext.getData(DataConstants.MODULE));
  }

  private class FileProjectOrModuleDialog extends DialogWrapper {
    private String myFileName;
    private String myModuleName;
    private JRadioButton myFileButton;
    private JRadioButton myProjectButton;
    private JRadioButton myModuleButton;
    private JCheckBox myInspectTestSource;
    private Project myProject;
    public FileProjectOrModuleDialog(Project project, String fileName, String moduleName, boolean nonJavaFile, boolean isProjectScope) {
      super(true);
      myProject = project;
      myFileName = fileName;
      myModuleName = moduleName;
      init();
      setTitle("Specify " + myTitle + " Scope");
      if (nonJavaFile) {
        myFileButton.setEnabled(false);
        myProjectButton.setSelected(true);
      }
      else {
        myFileButton.setSelected(true);
      }
      if (isProjectScope){
        myFileButton.setVisible(false);
        myProjectButton.setSelected(true);
      }
    }

    protected JComponent createCenterPanel() {
      JPanel wholePanel = new JPanel(new BorderLayout());
      JPanel panel = new JPanel();
      panel.setBorder(IdeBorderFactory.createTitledBorder(myAnalysisNoon + " scope"));
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      myFileButton = new JRadioButton(myAnalysisVerb + " " + StringUtil.decapitalize(myFileName));
      myFileButton.setMnemonic(KeyEvent.VK_P);
      myProjectButton = new JRadioButton(myAnalysisVerb + " the whole project");
      myProjectButton.setMnemonic(KeyEvent.VK_W);
      ButtonGroup group = new ButtonGroup();
      group.add(myProjectButton);
      group.add(myFileButton);
      panel.add(myProjectButton);
      if (myModuleName != null) {
        myModuleButton = new JRadioButton(myAnalysisVerb + " module \'" + myModuleName + "\'");
        myModuleButton.setMnemonic(KeyEvent.VK_M);
        group.add(myModuleButton);
        panel.add(myModuleButton);
      }
      panel.add(myFileButton);
      wholePanel.add(panel, BorderLayout.CENTER);
      myInspectTestSource = new JCheckBox("Include Test Sources", ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject)).getUIOptions().ANALYZE_TEST_SOURCES);
      wholePanel.add(myInspectTestSource, BorderLayout.SOUTH);
      return wholePanel;
    }

    public boolean isProjectScopeSelected() {
      return myProjectButton.isSelected();
    }

    public boolean isModuleScopeSelected() {
      return myModuleButton != null ? myModuleButton.isSelected() : false;
    }

    public boolean isInspectTestSources(){
      return myInspectTestSource.isSelected();
    }
  }
}