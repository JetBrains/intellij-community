package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public abstract class AbstractUrl {
  protected final String url;
  protected final String moduleName;
  private final String myType;

  protected AbstractUrl(String url, String moduleName, @NonNls String type) {
    myType = type;
    this.url = url == null ? "" : url;
    this.moduleName = moduleName;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void write(Element element) {
    element.setAttribute("url", url);
    if (moduleName != null) {
      element.setAttribute("module", moduleName);
    }
    element.setAttribute("type", myType);
  }

  public abstract Object[] createPath(Project project);

  // return null if cannot recognize the element
  public AbstractUrl checkMyUrl(String type, String moduleName, String url){
    if (type.equals(myType)) {
      return createUrl(moduleName, url);
    }
    return null;
  }
  protected abstract AbstractUrl createUrl(String moduleName, String url);
  public abstract AbstractUrl createUrlByElement(Object element);
}
