/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.actions.DoSearchAction;
import com.intellij.structuralsearch.MatchResult;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */
public class SSBasedInspection extends LocalInspectionTool {
  public SSBasedInspection() {
    int i=0;
  }

  public String getGroupDisplayName() {
    return "General";
  }

  public String getDisplayName() {
    return "SSR Inspection";
  }

  @NonNls
  public String getShortName() {
    return "SSBasedInspection";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    StructuralSearchPlugin searchPlugin = StructuralSearchPlugin.getInstance(file.getProject());
    List<Configuration> configurations = new ArrayList<Configuration>(searchPlugin.getConfigurationManager().getConfigurations());
    // todo: externalizing configs selected for inspection
    for (int i = configurations.size()-1; i>=0;i--) {
      Configuration configuration = configurations.get(i);
      String name = configuration.getName();
      //if (!name.startsWith("SSI")) configurations.remove(i);

      return performSearch(Collections.singleton(configuration), file, manager);
    }

    return null;
  }

  private static ProblemDescriptor[] performSearch(final Collection<Configuration> configurations,
                                            final PsiFile file,
                                            final InspectionManager manager) {
    Configuration configuration = configurations.iterator().next();
    configuration.getMatchOptions().setScope(GlobalSearchScope.fileScope(file));
    CollectingMatchResultSink sink = new CollectingMatchResultSink();
    DoSearchAction.execute(file.getProject(), sink, configuration);
    List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    List<MatchResult> matches = sink.getMatches();
    for (MatchResult matchResult : matches) {
      PsiElement element = matchResult.getMatch();
      String name = configuration.getName();
      ProblemDescriptor problemDescriptor =
        manager.createProblemDescriptor(element, name, (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      problems.add(problemDescriptor);
    }
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }
}
