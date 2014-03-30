/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.CustomEditInspectionToolsSettingsAction;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.inspections.PyPep8Inspection;
import com.jetbrains.python.inspections.quickfix.ReformatFix;
import com.jetbrains.python.quickFixes.RemoveTrailingBlankLinesFix;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class Pep8ExternalAnnotator extends ExternalAnnotator<Pep8ExternalAnnotator.State, Pep8ExternalAnnotator.Results> {
  private static final Logger LOG = Logger.getInstance(Pep8ExternalAnnotator.class);

  public static class Problem {
    private final int myLine;
    private final int myColumn;
    private final String myCode;
    private final String myDescription;

    public Problem(int line, int column, String code, String description) {
      myLine = line;
      myColumn = column;
      myCode = code;
      myDescription = description;
    }
  }

  public static class State {
    private final String interpreterPath;
    private final String fileText;
    private final HighlightDisplayLevel level;
    private final List<String> ignoredErrors;
    private final int margin;

    public State(String interpreterPath, String fileText, HighlightDisplayLevel level,
                 List<String> ignoredErrors, int margin) {
      this.interpreterPath = interpreterPath;
      this.fileText = fileText;
      this.level = level;
      this.ignoredErrors = ignoredErrors;
      this.margin = margin;
    }
  }

  public static class Results {
    public final List<Problem> problems = new ArrayList<Problem>();
    private final HighlightDisplayLevel level;

    public Results(HighlightDisplayLevel level) {
      this.level = level;
    }
  }

  private boolean myReportedMissingInterpreter;

  @Nullable
  @Override
  public State collectInformation(@NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null || vFile.getFileType() != PythonFileType.INSTANCE) {
      return null;
    }
    Sdk sdk = PythonSdkType.findLocalCPython(ModuleUtilCore.findModuleForPsiElement(file));
    if (sdk == null) {
      if (!myReportedMissingInterpreter) {
        myReportedMissingInterpreter = true;
        reportMissingInterpreter();
      }
      return null;
    }
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      if (!myReportedMissingInterpreter) {
        myReportedMissingInterpreter = true;
        LOG.info("Could not find home path for interpreter " + homePath);
      }
      return null;
    }
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getInspectionProfile();
    final HighlightDisplayKey key = HighlightDisplayKey.find(PyPep8Inspection.INSPECTION_SHORT_NAME);
    if (!profile.isToolEnabled(key)) {
      return null;
    }
    final PyPep8Inspection inspection = (PyPep8Inspection)profile.getUnwrappedTool(PyPep8Inspection.KEY.toString(), file);
    final List<String> ignoredErrors = inspection.ignoredErrors;
    final int margin = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings().RIGHT_MARGIN;
    return new State(homePath, file.getText(), profile.getErrorLevel(key, file), ignoredErrors, margin);
  }

  private static void reportMissingInterpreter() {
    LOG.info("Found no suitable interpreter to run pep.py. Available interpreters are: [");
    List<Sdk> allSdks = PythonSdkType.getAllSdks();
    Collections.sort(allSdks, PreferredSdkComparator.INSTANCE);
    for (Sdk sdk : allSdks) {
      LOG.info("  Path: " + sdk.getHomePath() + "; Flavor: " + PythonSdkFlavor.getFlavor(sdk) + "; Remote: " + PythonSdkType.isRemote(sdk));
    }
    LOG.info("]");
  }

  @Nullable
  @Override
  public Results doAnnotate(State collectedInfo) {
    if (collectedInfo == null) return null;
    final String pep8Path = PythonHelpersLocator.getHelperPath("pep8.py");
    ArrayList<String> options = new ArrayList<String>();
    Collections.addAll(options, collectedInfo.interpreterPath, pep8Path);
    if (collectedInfo.ignoredErrors.size() > 0) {
      options.add("--ignore=" + StringUtil.join(collectedInfo.ignoredErrors, ","));
    }
    options.add("--max-line-length=" + collectedInfo.margin);
    options.add("-");
    ProcessOutput output = PySdkUtil.getProcessOutput(new File(collectedInfo.interpreterPath).getParent(),
                                                      ArrayUtil.toStringArray(options),
                                                      new String[] { "PYTHONUNBUFFERED=1" },
                                                      10000,
                                                      collectedInfo.fileText.getBytes(), false);

    Results results = new Results(collectedInfo.level);
    if (output.isTimeout()) {
      LOG.info("Timeout running pep8.py");
    }
    else if (output.getStderrLines().isEmpty()) {
      for (String line : output.getStdoutLines()) {
        final Problem problem = parseProblem(line);
        if (problem != null) {
          results.problems.add(problem);
        }
      }
    }
    else if (((ApplicationInfoImpl) ApplicationInfo.getInstance()).isEAP()) {
      LOG.info("Error running pep8.py: " + output.getStderr());
    }
    return results;
  }

  @Override
  public void apply(@NotNull PsiFile file, Results annotationResult, @NotNull AnnotationHolder holder) {
    if (annotationResult == null) return;
    if (!file.isValid()) {
      LOG.info("Trying to apply diagnostics to invalid PsiFile, skipped");
      return;
    }
    final String text = file.getText();
    Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);

    for (Problem problem : annotationResult.problems) {
      if (ignoreDueToSettings(project, problem)) continue;
      final int line = problem.myLine - 1;
      final int column = problem.myColumn - 1;
      int offset;
      if (document != null) {
        offset = line >= document.getLineCount() ? document.getTextLength()-1 : document.getLineStartOffset(line) + column;
      }
      else {
        offset = StringUtil.lineColToOffset(text, line, column);
      }
      PsiElement problemElement = file.findElementAt(offset);
      if (!(problemElement instanceof PsiWhiteSpace) && !(problem.myCode.startsWith("E3"))) {
        final PsiElement elementAfter = file.findElementAt(offset + 1);
        if (elementAfter instanceof PsiWhiteSpace) {
          problemElement = elementAfter;
        }
      }
      if (problemElement != null) {
        TextRange problemRange = problemElement.getTextRange();
        if (crossesLineBoundary(document, text, problemRange)) {
          int lineEndOffset;
          if (document != null) {
            lineEndOffset = line >= document.getLineCount() ? document.getTextLength()-1 : document.getLineEndOffset(line);
          }
          else {
            lineEndOffset = StringUtil.lineColToOffset(text, line+1, 0) - 1;
          }
          if (offset > lineEndOffset) {
            // PSI/document don't match, don't try to highlight random places
            continue;
          }
          problemRange = new TextRange(offset, lineEndOffset);
        }
        final Annotation annotation;
        final String message = "PEP 8: " + problem.myDescription;
        if (annotationResult.level == HighlightDisplayLevel.ERROR) {
          annotation = holder.createErrorAnnotation(problemRange, message);
        }
        else if (annotationResult.level == HighlightDisplayLevel.WARNING) {
          annotation = holder.createWarningAnnotation(problemRange, message);
        }
        else {
          annotation = holder.createWeakWarningAnnotation(problemRange, message);
        }
        if (problem.myCode.equals("E401")) {
          annotation.registerUniversalFix(new OptimizeImportsQuickFix(), null, null);
        }
        else if (problem.myCode.equals("W391")) {
          annotation.registerUniversalFix(new RemoveTrailingBlankLinesFix(), null, null);
        }
        else {
          annotation.registerUniversalFix(new ReformatFix(), null, null);
        }
        annotation.registerFix(new IgnoreErrorFix(problem.myCode));
        annotation.registerFix(new CustomEditInspectionToolsSettingsAction(HighlightDisplayKey.find(PyPep8Inspection.INSPECTION_SHORT_NAME),
                                                                           new Computable<String>() {
                                                                             @Override
                                                                             public String compute() {
                                                                               return "Edit inspection profile setting";
                                                                             }
                                                                           }));
      }
    }
  }

  private static boolean crossesLineBoundary(@Nullable Document document, String text, TextRange problemRange) {
    int start = problemRange.getStartOffset();
    int end = problemRange.getEndOffset();
    if (document != null) {
      return document.getLineNumber(start) != document.getLineNumber(end);
    }
    return StringUtil.offsetToLineNumber(text, start) != StringUtil.offsetToLineNumber(text, end);
  }

  private static boolean ignoreDueToSettings(Project project, Problem problem) {
    String stripTrailingSpaces = EditorSettingsExternalizable.getInstance().getStripTrailingSpaces();
    if (!stripTrailingSpaces.equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE)) {
      // ignore trailing spaces errors if they're going to disappear after save
      if (problem.myCode.equals("W291") || problem.myCode.equals("W293")) {
        return true;
      }
    }
    boolean useTabs = CodeStyleSettingsManager.getSettings(project).useTabCharacter(PythonFileType.INSTANCE);
    if (useTabs && problem.myCode.equals("W191")) {
      return true;
    }
    return false;
  }

  private static final Pattern PROBLEM_PATTERN = Pattern.compile(".+:(\\d+):(\\d+): ([EW]\\d{3}) (.+)");

  @Nullable
  private static Problem parseProblem(String s) {
    Matcher m = PROBLEM_PATTERN.matcher(s);
    if (m.matches()) {
      int line = Integer.parseInt(m.group(1));
      int column = Integer.parseInt(m.group(2));
      return new Problem(line, column, m.group(3), m.group(4));
    }
    if (((ApplicationInfoImpl) ApplicationInfo.getInstance()).isEAP()) {
      LOG.info("Failed to parse problem line from pep8.py: " + s);
    }
    return null;
  }

  private static class IgnoreErrorFix implements IntentionAction {
    private final String myCode;

    public IgnoreErrorFix(String code) {
      myCode = code;
    }

    @NotNull
    @Override
    public String getText() {
      return "Ignore errors like this";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
      InspectionProjectProfileManager.getInstance(project).getInspectionProfile(file).modifyProfile(new Consumer<ModifiableModel>() {
        @Override
        public void consume(ModifiableModel model) {
          PyPep8Inspection tool = (PyPep8Inspection)model.getUnwrappedTool(PyPep8Inspection.INSPECTION_SHORT_NAME, file);
          tool.ignoredErrors.add(myCode);
        }
      });
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
