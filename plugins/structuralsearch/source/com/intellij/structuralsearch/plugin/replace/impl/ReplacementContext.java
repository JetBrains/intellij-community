package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.util.IncorrectOperationException;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 27.09.2005
 * Time: 14:27:20
 * To change this template use File | Settings | File Templates.
 */
class ReplacementContext {
  private PsiCodeBlock codeBlock;
  ReplacementInfoImpl replacementInfo;
  ReplaceOptions options;
  Project project;
  
  ReplacementContext(ReplaceOptions _options,Project _project) {
    options = _options;
    project = _project;
  }

  PsiCodeBlock getCodeBlock() throws IncorrectOperationException {
    if (codeBlock == null) {
      PsiCodeBlock search;
      search = (PsiCodeBlock)MatcherImplUtil.createTreeFromText(
        options.getMatchOptions().getSearchPattern(),
        false,
        options.getMatchOptions().getFileType(),
        project
      )[0].getParent();

      codeBlock = search;
    }
    return codeBlock;
  }
}
