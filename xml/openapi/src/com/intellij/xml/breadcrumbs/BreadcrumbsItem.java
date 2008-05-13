package com.intellij.xml.breadcrumbs;

/**
 * @author spleaner
 */
public abstract class BreadcrumbsItem {

  public abstract String getDisplayText();

  public String getTooltip() {
    return "";
  }

}
