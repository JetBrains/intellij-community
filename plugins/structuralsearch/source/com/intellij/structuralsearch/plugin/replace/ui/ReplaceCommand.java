package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.structuralsearch.plugin.ui.SearchCommand;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.usageView.UsageInfo;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 31, 2004
 * Time: 3:54:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class ReplaceCommand extends SearchCommand {
  private List<ReplacementInfo> resultPtrList;
  private Replacer replacer;
  private ReplaceOptions options;
  private List<UsageInfo> usages;

  public ReplaceCommand(SearchContext searchContext, Configuration _config) {
    super(searchContext, _config );
    options = ((ReplaceConfiguration)_config).getOptions();
    resultPtrList = new ArrayList<ReplacementInfo>(1);
    usages = new ArrayList<UsageInfo>(1);
    replacer = new Replacer(project, options);
  }

  protected void findStarted() {
    super.findStarted();

    StructuralSearchPlugin.getInstance(project).setReplaceInProgress(true);
  }

  protected void findEnded() {
    StructuralSearchPlugin.getInstance(project).setReplaceInProgress( false );

    super.findEnded();
  }

  protected void foundUsage(MatchResult result, UsageInfo usageInfo) {
    super.foundUsage(result, usageInfo);

    resultPtrList.add( replacer.buildReplacement(result) );
    usages.add(usageInfo);
  }

  public List<ReplacementInfo> getResultPtrList() {
    return resultPtrList;
  }

  public List<UsageInfo> getUsages() {
    return usages;
  }

  public ReplaceOptions getOptions() {
    return options;
  }

  public void reset() {
    resultPtrList.clear();
    usages.clear();
  }

  public Replacer getReplacer() {
    return replacer;
  }
}
