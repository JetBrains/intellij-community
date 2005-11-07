package com.intellij.structuralsearch.plugin.replace.ui;

import org.jdom.Element;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.MatchOptions;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 14, 2004
 * Time: 4:41:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class ReplaceConfiguration extends Configuration {
  private ReplaceOptions options = new ReplaceOptions();

  public ReplaceOptions getOptions() {
    return options;
  }

  public void setOptions(ReplaceOptions options) {
    this.options = options;
  }

  public MatchOptions getMatchOptions() {
    return options.getMatchOptions();
  }

  public void readExternal(Element element) {
    super.readExternal(element);
    options.readExternal(element);
  }

  public void writeExternal(Element element) {
    super.writeExternal(element);
    options.writeExternal(element);
  }

  public boolean equals(Object configuration) {
    if (!super.equals(configuration)) return false;
    if (configuration instanceof ReplaceConfiguration) {
      return options.equals(((ReplaceConfiguration)configuration).options);
    }
    return false;
  }

  public int hashCode() {
    return options.hashCode();
  }
}
