package com.intellij.structuralsearch;

import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import org.jetbrains.annotations.NonNls;

/**
 * Template info
 */
public class PredefinedConfiguration extends Configuration {
  public static final PredefinedConfiguration[] EMPTY_ARRAY = {};

  private final Configuration configuration;
  private final String category;

  public PredefinedConfiguration(Configuration configuration, String category) {
    this.configuration = configuration;
    this.category = category;
  }

  public static PredefinedConfiguration createSearchTemplateInfo(String name, @NonNls String criteria, String category) {
    return createSearchTemplateInfo(name, criteria, category, StdFileTypes.JAVA);
  }

  public static PredefinedConfiguration createSearchTemplateInfo(String name, @NonNls String criteria, String category, FileType fileType) {
    final SearchConfiguration config = new SearchConfiguration();
    config.setPredefined(true);
    config.setName(name);
    config.getMatchOptions().setSearchPattern(criteria);
    config.getMatchOptions().setFileType(fileType);
    MatcherImplUtil.transform( config.getMatchOptions() );

    return new PredefinedConfiguration(config,category);
  }

  public static PredefinedConfiguration createSearchTemplateInfoSimple(String name, @NonNls String criteria, String category) {
    final PredefinedConfiguration info = createSearchTemplateInfo(name,criteria,category);
    info.configuration.getMatchOptions().setRecursiveSearch(false);

    return info;
  }

  public String getCategory() {
    return category;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public String toString() {
    return configuration.getName();
  }

  public MatchOptions getMatchOptions() {
    return configuration.getMatchOptions();
  }

  public String getName() {
    return configuration.getName();
  }
}
