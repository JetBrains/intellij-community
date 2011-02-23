package com.intellij.structuralsearch.plugin.ui.actions;

import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.*;
import com.intellij.openapi.project.Project;

/**
 * Does the search action
 */
public class DoSearchAction {
  public static void execute(final Project project, MatchResultSink sink,
                             final Configuration configuration) {
    final MatchOptions options = configuration.getMatchOptions();

    Matcher matcher = new Matcher(project);

    sink = new FilteringMatchResultSink(sink);

    try {
      matcher.findMatches(sink, options);
    }
    finally {
      sink.matchingFinished();
    }
  }

}
