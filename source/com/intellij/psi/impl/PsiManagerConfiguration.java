package com.intellij.psi.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.application.ApplicationManager;
import org.jdom.Element;

public class PsiManagerConfiguration implements ApplicationComponent, JDOMExternalizable {
  public boolean REPOSITORY_ENABLED = true;
  public boolean CREATE_DUMMY_PROJECT_FOR_OBFUSCATION = true;

  public static PsiManagerConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(PsiManagerConfiguration.class);
  }

  public String getComponentName() {return "PsiManagerConfiguration"; }

  public void initComponent() { }

  public void disposeComponent() { }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }
}
