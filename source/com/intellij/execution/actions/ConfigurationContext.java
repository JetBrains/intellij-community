package com.intellij.execution.actions;

import com.intellij.execution.ConfigurationTypeEx;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.ConfigurationContext");
  private final Location<PsiElement> myLocation;
  private final DataContext myDataContext;
  private RunnerAndConfigurationSettings myConfiguration;

  public ConfigurationContext(final DataContext dataContext) {
    myDataContext = dataContext;
    final Object location = myDataContext.getData(Location.LOCATION);
    if (location != null) {
      myLocation = (Location<PsiElement>)location;
      return;
    }
    final Project project = (Project)myDataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      myLocation = null;
      return;
    }
    final PsiElement element = getSelectedPsiElement(dataContext, project);
    if (element == null) {
      myLocation = null;
      return;
    }
    myLocation = new PsiLocation<PsiElement>(project, element);
  }

  public RunnerAndConfigurationSettings getConfiguration() {
    if (myConfiguration == null) createConfiguration();
    return myConfiguration;
  }

  private void createConfiguration() {
    LOG.assertTrue(myConfiguration == null);
    final Location location = getLocation();
    myConfiguration = location != null ?
        new PreferedProducerFind().createConfiguration(location, this) :
        null;
  }

  private Location getLocation() {
    return myLocation;
  }

  public RunnerAndConfigurationSettings findExisting() {
    final ConfigurationType type = getConfiguration().getType();
    if (!(type instanceof ConfigurationTypeEx)) return null;
    final ConfigurationTypeEx factoryEx = (ConfigurationTypeEx)type;
    final RunnerAndConfigurationSettings[] configurations = getRunManager().getConfigurationSettings(type);
    for (int i = 0; i < configurations.length; i++) {
      final RunnerAndConfigurationSettings existingConfiguration = configurations[i];
      if (factoryEx.isConfigurationByElement(existingConfiguration.getConfiguration(), getProject(), myLocation.getPsiElement())){
        return existingConfiguration;
      }
    }
    return null;
  }

  private static PsiElement getSelectedPsiElement(final DataContext dataContext, final Project project) {
    PsiElement element = null;
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null){
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      }
    }
    if (element == null) {
      element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    }
    if (element == null) {
      final VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
      if (file != null) {
        element = PsiManager.getInstance(project).findFile(file);
      }
    }
    return element;
  }

  public RunManager getRunManager() {
    return RunManager.getInstance(getProject());
  }

  public Project getProject() { return myLocation.getProject(); }

  public DataContext getDataContext() {
    return myDataContext;
  }

  public RunnerAndConfigurationSettings getOriginalConfiguration(final ConfigurationType type) {
    final RunnerAndConfigurationSettings config = (RunnerAndConfigurationSettings)myDataContext.getData(DataConstantsEx.RUNTIME_CONFIGURATION);
    return config != null && type.equals(config.getType()) ? config : null ;
  }
}
