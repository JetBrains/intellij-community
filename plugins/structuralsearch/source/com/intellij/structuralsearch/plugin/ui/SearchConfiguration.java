package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.openapi.actionSystem.AnAction;
import org.jdom.Element;
import org.jdom.Attribute;
import org.jdom.DataConversionException;

/**
 * Configuration of the search
 */
public class SearchConfiguration extends Configuration {
  private MatchOptions matchOptions;

  public SearchConfiguration() {
    matchOptions = new MatchOptions();
  }

  public MatchOptions getMatchOptions() {
    return matchOptions;
  }

  public void setMatchOptions(MatchOptions matchOptions) {
    this.matchOptions = matchOptions;
  }

  public void readExternal(Element element) {
    super.readExternal(element);

    matchOptions.readExternal(element);
  }

  public void writeExternal(Element element) {
    super.writeExternal(element);

    matchOptions.writeExternal(element);
  }

}
