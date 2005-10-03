package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.structuralsearch.plugin.ui.UsageViewContext;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchCommand;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.usages.Usage;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 9, 2005
 * Time: 4:37:08 PM
 * To change this template use File | Settings | File Templates.
 */
class ReplaceUsageViewContext extends UsageViewContext {
  private HashMap<Usage,ReplacementInfo> usage2ReplacementInfo;
  private java.util.List<ReplacementInfo> results;
  private Replacer replacer;

  ReplaceUsageViewContext(final SearchContext _context, final Configuration _configuration) {
    super(_context,_configuration);
  }

  protected SearchCommand createCommand() {
    ReplaceCommand command = new ReplaceCommand(mySearchContext.getProject(), this);

    usage2ReplacementInfo = new HashMap<Usage, ReplacementInfo>();
    results = new ArrayList<ReplacementInfo>();
    replacer = new Replacer(mySearchContext.getProject(), ((ReplaceConfiguration)myConfiguration).getOptions());

    return command;
  }

  protected String _getPresentableText() {
    return SSRBundle.message("replaceusageview.text",
                             getConfiguration().getMatchOptions().getSearchPattern(),
                            ((ReplaceConfiguration)getConfiguration()).getOptions().getReplacement()
    );
  }

  public HashMap<Usage, ReplacementInfo> getUsage2ReplacementInfo() {
    return usage2ReplacementInfo;
  }

  public List<ReplacementInfo> getResults() {
    return results;
  }

  public Replacer getReplacer() {
    return replacer;
  }

  public void addReplaceUsage(final Usage usage, final ReplacementInfo replacementInfo) {
    usage2ReplacementInfo.put(usage,replacementInfo);
  }
}
