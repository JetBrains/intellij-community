package com.intellij.debugger.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class ThreadsViewSettings implements NamedJDOMExternalizable, ApplicationComponent {
  public boolean SHOW_THREAD_GROUPS = false;
  public boolean SHOW_LINE_NUMBER = true;
  public boolean SHOW_CLASS_NAME = true;
  public boolean SHOW_SOURCE_NAME = false;
  public boolean SHOW_SYNTHETIC_FRAMES = true;
  public boolean SHOW_CURRENT_THREAD = true;
  private Configurable myConfigurable;

  public Configurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = new ThreadsViewConfigurable(this);
    }
    return myConfigurable;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getExternalFileName() {
    return "debugger.threadsview";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public String getComponentName() {
    return "ThreadsViewSettings";
  }

  public static ThreadsViewSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(ThreadsViewSettings.class);
 }

}