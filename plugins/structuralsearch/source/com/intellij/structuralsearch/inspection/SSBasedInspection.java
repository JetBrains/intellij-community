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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SSBasedInspection extends LocalInspectionTool {
  private List<Configuration> myConfigurations = new ArrayList<Configuration>();
  private MatcherImpl.CompiledOptions compiledConfigurations;

  public SSBasedInspection() {
  }

  public void writeSettings(Element node) throws WriteExternalException {
    ConfigurationManager.writeConfigurations(node, myConfigurations, Collections.<Configuration>emptyList());
  }

  public void readSettings(Element node) throws InvalidDataException {
    myConfigurations.clear();
    ConfigurationManager.readConfigurations(node, myConfigurations, new ArrayList<Configuration>());

    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        precompileConfigurations();
      }
    });
  }

  public String getGroupDisplayName() {
    return "General";
  }

  public String getDisplayName() {
    return "Structural Search Inspection";
  }

  @NonNls
  public String getShortName() {
    return "SSBasedInspection";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    Project project = file.getProject();

    if (compiledConfigurations == null) return null;
    Collection<Pair<MatchResult,Configuration>> matches = new Matcher(project).findMatchesInFile(compiledConfigurations, file);

    List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    for (Pair<MatchResult,Configuration> pair : matches) {
      MatchResult matchResult = pair.first;
      Configuration configuration = pair.second;
      PsiElement element = matchResult.getMatch();
      String name = configuration.getName();
      LocalQuickFix fix = createQuickFix(project, matchResult, configuration);
      ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(element, name, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      problems.add(problemDescriptor);
    }
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private LocalQuickFix createQuickFix(final Project project, final MatchResult matchResult, final Configuration configuration) {
    if (!(configuration instanceof ReplaceConfiguration)) return null;
    ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)configuration;
    final Replacer replacer = new Replacer(project, replaceConfiguration.getOptions());
    final ReplacementInfo replacementInfo = replacer.buildReplacement(matchResult);

    return new LocalQuickFix() {
      public String getName() {
        return "Replace with '"+replacementInfo.getReplacement()+"'";
      }

      public void applyFix(Project project, ProblemDescriptor descriptor) {
        replacer.replace(replacementInfo);
      }

      public String getFamilyName() {
        return "Replace Structurally";
      }
    };
  }

  @Nullable
  public JComponent createOptionsPanel() {
    Project project = ProjectManager.getInstance().getOpenProjects()[0];
    JPanel component = new SSBasedInspectionOptions(project, myConfigurations){
      public void configurationsChanged() {
        super.configurationsChanged();
        precompileConfigurations();
      }
    }.getComponent();
    return component;
  }

  // must be inside event dispatch
  public void precompileConfigurations() {
    Project project = ProjectManager.getInstance().getOpenProjects()[0];

    compiledConfigurations = new Matcher(project).precompileOptions(myConfigurations);
  }
}
