package com.intellij.compiler;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class RmicSettings implements JDOMExternalizable, ProjectComponent {
  public boolean IS_EANABLED = false;
  public boolean DEBUGGING_INFO = true;
  public boolean GENERATE_NO_WARNINGS = false;
  public boolean GENERATE_IIOP_STUBS = false;
  public String ADDITIONAL_OPTIONS_STRING = "";

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public String[] getOptions() {
    List<String> options = new ArrayList<String>();
    if(DEBUGGING_INFO) {
      options.add("-g");
    }
    if(GENERATE_NO_WARNINGS) {
      options.add("-nowarn");
    }
    if(GENERATE_IIOP_STUBS) {
      options.add("-iiop");
    }
    final StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if("-g".equals(token)) {
        continue;
      }
      if("-iiop".equals(token)) {
        continue;
      }
      if("-nowarn".equals(token)) {
        continue;
      }
      options.add(token);
    }
    return options.toArray(new String[options.size()]);
  }

  public static RmicSettings getInstance(Project project) {
    return project.getComponent(RmicSettings.class);
  }

  public String getComponentName() {
    return "RmicSettings";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}