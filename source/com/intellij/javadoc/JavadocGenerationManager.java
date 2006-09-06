package com.intellij.javadoc;

import com.intellij.CommonBundle;
import com.intellij.ant.impl.MapDataContext;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

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
    final PsiPackage aPackage = directory != null ? directory.getPackage() : null;
    String packageFQName = aPackage != null ? aPackage.getQualifiedName() : null;

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

    if (dialog.isGenerationForPackage() && !dialog.isGenerationWithSubpackages()) {
      //remove package prefixes from javadoc.exe command
      final Module module = directory != null ? ModuleUtil.findModuleForPsiElement(directory) : null;
      if (module != null && packageFQName != null){
        boolean reset = false;
        final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
        for (ContentEntry contentEntry : contentEntries) {
          final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
          for (SourceFolder sourceFolder : sourceFolders) {
            final String packagePrefix = sourceFolder.getPackagePrefix();
            final int prefixLength = packagePrefix.length();
            if (prefixLength > 0 && packageFQName.startsWith(packagePrefix) && packageFQName.length() > prefixLength){
              packageFQName = packageFQName.substring(prefixLength + 1);
              reset = true;
              break;
            }
            if (reset) break;
          }
        }
      }
    }

    myConfiguration.setGenerationOptions(new JavadocConfiguration.GenerationOptions(packageFQName, directory, dialog.isGenerationForPackage(),
                                                                                    dialog.isGenerationWithSubpackages()));
    try {
      RunStrategy.getInstance().executeDefault(myConfiguration, dataContext);
    }
    catch (ExecutionException e) {
      ExecutionUtil.showExecutionErrorMessage(e, CommonBundle.getErrorTitle(), myProject);
    }
  }


  @NotNull
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