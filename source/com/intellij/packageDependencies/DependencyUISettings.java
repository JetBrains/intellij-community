package com.intellij.packageDependencies;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class DependencyUISettings implements JDOMExternalizable, ApplicationComponent {
  public boolean UI_FLATTEN_PACKAGES = true;
  public boolean UI_SHOW_FILES = false;
  public boolean UI_SHOW_MODULES = true;
  public boolean UI_FILTER_LEGALS = false;
  public boolean UI_GROUP_BY_SCOPE_TYPE = true;

  public static DependencyUISettings getInstance() {
    return ApplicationManager.getApplication().getComponent(DependencyUISettings.class);
  }

  public String getComponentName() {
    return "DependencyUISettings";
  }

  public void initComponent() {}
  public void disposeComponent() {}

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}