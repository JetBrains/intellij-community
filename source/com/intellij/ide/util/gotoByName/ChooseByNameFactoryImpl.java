package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public class ChooseByNameFactoryImpl extends ChooseByNameFactory {
  private Project myProject;

  public ChooseByNameFactoryImpl(final Project project) {
    myProject = project;
  }

  public ChooseByNamePopup createChooseByNamePopupComponent(final ChooseByNameModel model) {
    return ChooseByNamePopup.createPopup(myProject, model);  
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "ChooseByNameFactoryImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
