package com.intellij.analysis;

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
import java.awt.event.KeyEvent;

public abstract class BaseAnalysisAction extends AnAction {
  protected final AnalysisScope.PsiFileFilter myFileFilter;
  protected final String myTitle;
  protected final String myAnalysisVerb;
  protected final String myAnalysisNoon;

  protected BaseAnalysisAction(AnalysisScope.PsiFileFilter fileFilter, String title, String analysisVerb, String analysisNoon) {
    myFileFilter = fileFilter;
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
        scope = new AnalysisScope(psiFile, myFileFilter);
        calledFromNonJavaFile = true;
      }
      else {
        scope = getInspectionScope(dataContext);
      }
      if (calledFromNonJavaFile ||
          scope.getScopeType() == AnalysisScope.FILE &&
          (ActionPlaces.MAIN_MENU.equals(e.getPlace()) || ActionPlaces.EDITOR_POPUP.equals(e.getPlace()))) {
        FileOrProjectDialog dlg = new FileOrProjectDialog(scope.getDisplayName(),
                                                          module != null ? ModuleUtil.getModuleNameInReadAction(module) : null,
                                                          calledFromNonJavaFile);
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
      }

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
      return new AnalysisScope(projectContext, myFileFilter);
    }

    Module moduleContext = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext, myFileFilter);
    }

    PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    if (psiFile != null) {
      return psiFile instanceof PsiJavaFile ? new AnalysisScope(psiFile, myFileFilter) : null;
    }

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
    else if (psiTarget != null) {
      return null;
    }

    return getProjectScope(dataContext);
  }

  private AnalysisScope getProjectScope(DataContext dataContext) {
    return new AnalysisScope((Project)dataContext.getData(DataConstants.PROJECT), myFileFilter);
  }

  private AnalysisScope getModuleScope(DataContext dataContext) {
    return new AnalysisScope((Module)dataContext.getData(DataConstants.MODULE), myFileFilter);
  }

  private class FileOrProjectDialog extends DialogWrapper {
    private String myFileName;
    private String myModuleName;
    private JRadioButton myFileButton;
    private JRadioButton myProjectButton;
    private JRadioButton myModuleButton;

    public FileOrProjectDialog(String fileName, String moduleName, boolean nonJavaFile) {
      super(true);
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
    }

    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel();
      panel.setBorder(IdeBorderFactory.createTitledBorder(myAnalysisNoon + " scope"));
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      myFileButton = new JRadioButton(myAnalysisVerb + " " + StringUtil.decapitalize(myFileName));
      myFileButton.setMnemonic(KeyEvent.VK_F);
      myProjectButton = new JRadioButton(myAnalysisVerb + " the whole project");
      myProjectButton.setMnemonic(KeyEvent.VK_P);
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
      return panel;
    }

    public boolean isProjectScopeSelected() {
      return myProjectButton.isSelected();
    }

    public boolean isModuleScopeSelected() {
      return myModuleButton != null ? myModuleButton.isSelected() : false;
    }
  }
}