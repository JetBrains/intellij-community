package com.intellij.codeEditor.printing;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 *
 */
public class ExportToHTMLSettings implements NamedJDOMExternalizable, ProjectComponent {
  public boolean PRINT_LINE_NUMBERS;
  public boolean OPEN_IN_BROWSER;
  public String OUTPUT_DIRECTORY;

  private int myPrintScope;

  private boolean isIncludeSubpackages = false;
  private boolean isGenerateHyperlinksToClasses = false;

  public static ExportToHTMLSettings getInstance(Project project) {
    return project.getComponent(ExportToHTMLSettings.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getExternalFileName() {
    return "export2html";
  }

  public int getPrintScope() {
    return myPrintScope;
  }

  public void setPrintScope(int printScope) {
    myPrintScope = printScope;
  }

  public boolean isIncludeSubdirectories() {
    return isIncludeSubpackages;
  }

  public void setIncludeSubpackages(boolean includeSubpackages) {
    isIncludeSubpackages = includeSubpackages;
  }

  public boolean isGenerateHyperlinksToClasses() {
    return isGenerateHyperlinksToClasses;
  }

  public void setGenerateHyperlinksToClasses(boolean generateHyperlinksToClasses) {
    isGenerateHyperlinksToClasses = generateHyperlinksToClasses;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public String getComponentName() {
    return "ExportToHTMLSettings";
  }

}
