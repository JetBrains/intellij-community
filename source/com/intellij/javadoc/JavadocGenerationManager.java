package com.intellij.javadoc;

import com.intellij.ant.impl.MapDataContext;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDirectory;
import org.jdom.Element;

import java.awt.*;

public final class JavadocGenerationManager implements JDOMExternalizable, ProjectComponent {
  private final Project myProject;
  private final JavadocConfiguration myConfiguration;

  public static JavadocGenerationManager getInstance(Project project) {
    return project.getComponent(JavadocGenerationManager.class);
  }

  JavadocGenerationManager(Project project) {
    myProject = project;
    myConfiguration = new JavadocConfiguration(project);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void generateJavadoc(final PsiDirectory directory, DataContext dataContext) {
    Component component = (Component)dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT);
    final String packageFQName =
      directory != null && directory.getPackage() != null ?
      directory.getPackage().getQualifiedName() :
      null;

    final GenerateJavadocDialog dialog = new GenerateJavadocDialog(packageFQName, myProject, myConfiguration);
    dialog.reset();
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    if (component != null) {
      dataContext = DataManager.getInstance().getDataContext(component);
    }
    else {
      dataContext = MapDataContext.singleData(DataConstants.PROJECT, myProject);
    }

    myConfiguration.setGenerationOptions(new JavadocConfiguration.GenerationOptions(packageFQName, directory, dialog.isGenerationForPackage(),
                                                                                    dialog.isGenerationWithSubpackages()));
    try {
      RunStrategy.getInstance().executeDefault(myConfiguration, dataContext);
    }
    catch (ExecutionException e) {
      ExecutionUtil.showExecutionErrorMessage(e, "Error", myProject);
    }
  }


  public String getComponentName() {
    return "JavadocGenerationManager";
  }

  public void readExternal(Element element) throws InvalidDataException {
    myConfiguration.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myConfiguration.writeExternal(element);
  }

  public JavadocConfiguration getConfiguration() {
    return myConfiguration;
  }

}