package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.structuralsearch.plugin.ui.SearchCommand;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.openapi.project.Project;
import com.intellij.usages.Usage;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 31, 2004
 * Time: 3:54:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class ReplaceCommand extends SearchCommand {
  private final ReplaceOptions options;

  public ReplaceCommand(Project project, ReplaceUsageViewContext context) {
    super( project, context );
    options = ((ReplaceConfiguration)context.getConfiguration()).getOptions();

  }

  protected void findStarted() {
    super.findStarted();

    StructuralSearchPlugin.getInstance(project).setReplaceInProgress(true);
  }

  protected void findEnded() {
    StructuralSearchPlugin.getInstance(project).setReplaceInProgress( false );

    super.findEnded();
  }

  protected void foundUsage(MatchResult result, Usage usage) {
    super.foundUsage(result, usage);

    final ReplaceUsageViewContext replaceUsageViewContext = ((ReplaceUsageViewContext)context);
    replaceUsageViewContext.addReplaceUsage(usage,replaceUsageViewContext.getReplacer().buildReplacement(result));
  }

  public ReplaceOptions getOptions() {
    return options;
  }
}
