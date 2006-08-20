package com.intellij.find;

import com.intellij.openapi.project.Project;

/**
 * @author ven
 */
public class FindProgressIndicator extends BackgroundableProcessIndicator {
  public FindProgressIndicator(Project project, String scopeString) {
    super(project,
         FindBundle.message("find.progress.searching.message", scopeString),
         FindBundle.message("find.progress.stop.title"),
         FindBundle.message("find.progress.stop.background.button"));
  }
}
