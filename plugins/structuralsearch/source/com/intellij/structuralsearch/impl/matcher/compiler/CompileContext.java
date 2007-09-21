package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:26:37
 * To change this template use File | Settings | File Templates.
 */
class CompileContext {
  OptimizingSearchHelper searchHelper;
  
  CompiledPattern pattern;
  MatchOptions options;
  Project project;

  public void clear() {
    searchHelper.clear();

    project = null;
    pattern = null;
    options = null;
  }

  public void init(final CompiledPattern _result, final MatchOptions _options, final Project _project, final boolean _findMatchingFiles) {
    options = _options;
    project = _project;
    pattern = _result;

    searchHelper = ApplicationManager.getApplication().isUnitTestMode() ?
                   new TestModeOptimizingSearchHelper(this) :
                   new FindInFilesOptimizingSearchHelper(this, _findMatchingFiles, _project);
  }
}
