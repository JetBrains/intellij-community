package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 25, 2004
 * Time: 1:33:10 PM
 * To change this template use File | Settings | File Templates.
 */
public class SearchModel {
  private Configuration config;
  private Configuration shadowConfig;

  public SearchModel(Configuration _config) {
    config = _config;
  }

  public Configuration getConfig() {
    return config;
  }

  public void setShadowConfig(Configuration _shadowConfig) {
    shadowConfig = _shadowConfig;
  }

  public Configuration getShadowConfig() {
    return shadowConfig;
  }
}
