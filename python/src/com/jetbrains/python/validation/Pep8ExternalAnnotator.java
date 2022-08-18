// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.CustomEditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.*;
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.inspections.PyPep8Inspection;
import com.jetbrains.python.inspections.flake8.Flake8InspectionSuppressor;
import com.jetbrains.python.inspections.quickfix.PyFillParagraphFix;
import com.jetbrains.python.inspections.quickfix.ReformatFix;
import com.jetbrains.python.inspections.quickfix.RemoveTrailingBlankLinesFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.as;


public class Pep8ExternalAnnotator extends ExternalAnnotator<Pep8ExternalAnnotator.State, Pep8ExternalAnnotator.Results> {
  // Taken directly from the sources of pycodestyle.py
  private static final String DEFAULT_IGNORED_ERRORS = "E121,E123,E126,E226,E24,E704,W503,W504";
  private static final Logger LOG = Logger.getInstance(Pep8ExternalAnnotator.class);
  private static final Pattern E303_LINE_COUNT_PATTERN = Pattern.compile(".*\\((\\d+)\\)$");

  public static class Problem {
    private final int myLine;
    private final int myColumn;
    private final String myCode;
    private final String myDescription;

    public Problem(int line, int column, @NotNull String code, @NotNull String description) {
      myLine = line;
      myColumn = column;
      myCode = code;
      myDescription = description;
    }

    public int getLine() {
      return myLine;
    }

    public int getColumn() {
      return myColumn;
    }

    @NotNull
    public String getCode() {
      return myCode;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }
  }

  public static class State {
    private final String interpreterPath;
    private final String fileText;
    private final HighlightDisplayLevel level;
    private final List<String> ignoredErrors;
    private final int margin;
    private final boolean hangClosingBrackets;

    public State(String interpreterPath, String fileText, HighlightDisplayLevel level,
                 List<String> ignoredErrors, int margin, boolean hangClosingBrackets) {
      this.interpreterPath = interpreterPath;
      this.fileText = fileText;
      this.level = level;
      this.ignoredErrors = ignoredErrors;
      this.margin = margin;
      this.hangClosingBrackets = hangClosingBrackets;
    }
  }

  public static class Results {
    public final List<Problem> problems = new ArrayList<>();
    private final HighlightDisplayLevel level;

    public Results(HighlightDisplayLevel level) {
      this.level = level;
    }
  }

  private boolean myReportedMissingInterpreter;

  @Override
  public String getPairedBatchInspectionShortName() {
    return PyPep8Inspection.INSPECTION_SHORT_NAME;
  }

  @Nullable
  @Override
  public State collectInformation(@NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null || !FileTypeRegistry.getInstance().isFileOfType(vFile, PythonFileType.INSTANCE)) {
      return null;
    }
    Sdk sdk = PythonSdkType.findLocalCPython(DocStringUtil.getModuleForElement(file));
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
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getCurrentProfile();
    final HighlightDisplayKey key = HighlightDisplayKey.find(PyPep8Inspection.INSPECTION_SHORT_NAME);
    if (!profile.isToolEnabled(key, file)) {
      return null;
    }
    if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(PyPep8Inspection.class)) {
      return null;
    }
    final PyPep8Inspection inspection = (PyPep8Inspection)profile.getUnwrappedTool(PyPep8Inspection.INSPECTION_SHORT_NAME, file);
    final CodeStyleSettings commonSettings = CodeStyle.getSettings(file);
    final PyCodeStyleSettings customSettings = CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class);

    final List<String> ignoredErrors = Lists.newArrayList(inspection.ignoredErrors);
    if (!customSettings.SPACE_AFTER_NUMBER_SIGN) {
      ignoredErrors.add("E262"); // Block comment should start with a space
      ignoredErrors.add("E265"); // Inline comment should start with a space
    }

    if (!customSettings.SPACE_BEFORE_NUMBER_SIGN) {
      ignoredErrors.add("E261"); // At least two spaces before inline comment
    }

    return new State(homePath, file.getText(), profile.getErrorLevel(key, file),
                     ignoredErrors, commonSettings.getRightMargin(PythonLanguage.getInstance()), customSettings.HANG_CLOSING_BRACKETS);
  }

  private static void reportMissingInterpreter() {
    LOG.info("Found no suitable interpreter to run pycodestyle.py. Available interpreters are: [");
    List<Sdk> allSdks = PythonSdkUtil.getAllSdks();
    allSdks.sort(PreferredSdkComparator.INSTANCE);
    for (Sdk sdk : allSdks) {
      LOG.info("  Path: " + sdk.getHomePath() + "; Flavor: " + PythonSdkFlavor.getFlavor(sdk) + "; Remote: " + PythonSdkUtil.isRemote(sdk));
    }
    LOG.info("]");
  }

  @Nullable
  @Override
  public Results doAnnotate(State collectedInfo) {
    if (collectedInfo == null) return null;
    ArrayList<String> options = new ArrayList<>();

    if (!collectedInfo.ignoredErrors.isEmpty()) {
      options.add("--ignore=" + DEFAULT_IGNORED_ERRORS + "," + StringUtil.join(collectedInfo.ignoredErrors, ","));
    }
    if (collectedInfo.hangClosingBrackets) {
      options.add("--hang-closing");
    }
    options.add("--max-line-length=" + collectedInfo.margin);
    options.add("-");

    GeneralCommandLine cmd = PythonHelper.PYCODESTYLE.newCommandLine(collectedInfo.interpreterPath, options);

    ProcessOutput output = PySdkUtil.getProcessOutput(cmd, new File(collectedInfo.interpreterPath).getParent(),
                                                      ImmutableMap.of("PYTHONBUFFERED", "1"),
                                                      10000,
                                                      collectedInfo.fileText.getBytes(StandardCharsets.UTF_8), false);

    Results results = new Results(collectedInfo.level);
    if (output.isTimeout()) {
      LOG.info("Timeout running pycodestyle.py");
      return results;
    }
    if (!output.getStderr().isEmpty() && ((ApplicationInfoImpl) ApplicationInfo.getInstance()).isEAP()) {
      LOG.info("Error running pycodestyle.py: " + output.getStderr());
    }
    for (String line : output.getStdoutLines()) {
      ContainerUtil.addIfNotNull(results.problems, parseProblem(line));
    }
    return results;
  }

  @Override
  public void apply(@NotNull PsiFile file, Results annotationResult, @NotNull AnnotationHolder holder) {
    if (annotationResult == null || !file.isValid()) return;
    final String text = file.getText();
    Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);

    for (Problem problem : annotationResult.problems) {
      final int line = problem.myLine - 1;
      final int column = problem.myColumn - 1;
      int offset;
      if (document != null) {
        offset = line >= document.getLineCount() ? document.getTextLength() - 1 : document.getLineStartOffset(line) + column;
      }
      else {
        offset = StringUtil.lineColToOffset(text, line, column);
      }
      PsiElement problemElement = file.findElementAt(offset);
      // E3xx - blank lines warnings
      if (!(problemElement instanceof PsiWhiteSpace) && problem.myCode.startsWith("E3")) {
        final PsiElement elementBefore = file.findElementAt(Math.max(0, offset - 1));
        if (elementBefore instanceof PsiWhiteSpace) {
          problemElement = elementBefore;
        }
      }
      // W292 no newline at end of file
      if (problemElement == null && document != null && offset == document.getTextLength() && problem.myCode.equals("W292")) {
        problemElement = file.findElementAt(Math.max(0, offset - 1));
      }

      if (ignoreDueToSettings(file, problem, problemElement) ||
          ignoredDueToProblemSuppressors(problem, file, problemElement) ||
          ignoredDueToNoqaComment(problem, file, document)) {
        continue;
      }

      if (problemElement != null) {
        TextRange problemRange = problemElement.getTextRange();
        // Multi-line warnings are shown only in the gutter and it's not the desired behavior from the usability point of view.
        // So we register it only on that line where pycodestyle.py found the problem originally.
        if (crossesLineBoundary(document, text, problemRange)) {
          final int lineEndOffset;
          if (document != null) {
            lineEndOffset = line >= document.getLineCount() ? document.getTextLength() - 1 : document.getLineEndOffset(line);
          }
          else {
            lineEndOffset = StringUtil.lineColToOffset(text, line + 1, 0) - 1;
          }
          if (offset > lineEndOffset) {
            // PSI/document don't match, don't try to highlight random places
            continue;
          }
          problemRange = new TextRange(offset, lineEndOffset);
        }

        @NonNls
        final String message = "PEP 8: " + problem.myCode + " " + problem.myDescription;
        HighlightSeverity severity;
        if (annotationResult.level == HighlightDisplayLevel.ERROR) {
          severity = HighlightSeverity.ERROR;
        }
        else if (annotationResult.level == HighlightDisplayLevel.WARNING) {
          severity = HighlightSeverity.WARNING;
        }
        else {
          severity = HighlightSeverity.WEAK_WARNING;
        }
        IntentionAction fix;
        boolean universal;
        if (problem.myCode.equals("E401")) {
          fix = new OptimizeImportsQuickFix();
          universal = true;
        }
        else if (problem.myCode.equals("W391")) {
          fix = new RemoveTrailingBlankLinesFix();
          universal = true;
        }
        else if (problem.myCode.equals("E501")) {
          fix = new PyFillParagraphFix();
          universal = false;
        }
        else {
          fix = new ReformatFix();
          universal = true;
        }
        AnnotationBuilder builder = holder.newAnnotation(severity, message).range(problemRange);
        if (universal) {
          builder = builder.newFix(fix).universal().registerFix();
        }
        else {
          builder = builder.withFix(fix);
        }
        builder
          .withFix(new IgnoreErrorFix(problem.myCode))
          .withFix(new CustomEditInspectionToolsSettingsAction(HighlightDisplayKey.find(PyPep8Inspection.INSPECTION_SHORT_NAME),
                                                               () -> PyBundle.message("QFIX.pep8.edit.inspection.profile.setting"))).create();
      }
    }
  }

  private static boolean ignoredDueToProblemSuppressors(@NotNull Problem problem,
                                                        @NotNull PsiFile file,
                                                        @Nullable PsiElement element) {
    final Pep8ProblemSuppressor[] suppressors = Pep8ProblemSuppressor.EP_NAME.getExtensions();
    return Arrays.stream(suppressors).anyMatch(p -> p.isProblemSuppressed(problem, file, element));
  }

  private static boolean ignoredDueToNoqaComment(@NotNull Problem problem, @NotNull PsiFile file, @Nullable Document document) {
    if (document == null) {
      return false;
    }
    final int reportedLine = problem.myLine - 1;
    final int lineLastOffset = Math.max(document.getLineStartOffset(reportedLine), document.getLineEndOffset(reportedLine) - 1);
    final PsiComment comment = as(file.findElementAt(lineLastOffset), PsiComment.class);
    if (comment != null) {
      final Set<String> codes = Flake8InspectionSuppressor.extractNoqaCodes(comment);
      if (codes != null) {
        return codes.isEmpty() || ContainerUtil.exists(codes, code -> problem.myCode.startsWith(code));
      }
    }
    return false;
  }

  private static boolean crossesLineBoundary(@Nullable Document document, String text, TextRange problemRange) {
    int start = problemRange.getStartOffset();
    int end = problemRange.getEndOffset();
    if (document != null) {
      return document.getLineNumber(start) != document.getLineNumber(end);
    }
    return StringUtil.offsetToLineNumber(text, start) != StringUtil.offsetToLineNumber(text, end);
  }

  private static boolean ignoreDueToSettings(PsiFile file, Problem problem, @Nullable PsiElement element) {
    final EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    if (!editorSettings.getStripTrailingSpaces().equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE)) {
      // ignore trailing spaces errors if they're going to disappear after save
      if (problem.myCode.equals("W291") || problem.myCode.equals("W293")) {
        return true;
      }
    }

    final CommonCodeStyleSettings commonSettings = CodeStyle.getLanguageSettings(file, PythonLanguage.getInstance());
    final PyCodeStyleSettings pySettings = CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class);

    if (element instanceof PsiWhiteSpace) {
      // E303 too many blank lines (num)
      if (problem.myCode.equals("E303")) {
        final Matcher matcher = E303_LINE_COUNT_PATTERN.matcher(problem.myDescription);
        if (matcher.matches()) {
          final int reportedBlanks = Integer.parseInt(matcher.group(1));
          final PsiElement nonWhitespaceAfter = PyPsiUtils.getNextNonWhitespaceSibling(element);
          final PsiElement nonWhitespaceBefore = PyPsiUtils.getPrevNonWhitespaceSibling(element);
          final boolean classNearby = nonWhitespaceBefore instanceof PyClass || nonWhitespaceAfter instanceof PyClass;
          final boolean functionNearby = nonWhitespaceBefore instanceof PyFunction || nonWhitespaceAfter instanceof PyFunction;
          if (functionNearby || classNearby) {
            if (PyUtil.isTopLevel(element)) {
              if (reportedBlanks <= pySettings.BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS) {
                return true;
              }
            }
            else {
              // Blanks around classes have priority over blanks around functions as defined in Python spacing builder
              if (classNearby && reportedBlanks <= commonSettings.BLANK_LINES_AROUND_CLASS ||
                  functionNearby && reportedBlanks <= commonSettings.BLANK_LINES_AROUND_METHOD) {
                return true;
              }
            }
          }
        }
      }

      // E251 unexpected spaces around keyword / parameter equals
      // Note that E222 (multiple spaces after operator) is not suppressed, though.
      if (problem.myCode.equals("E251") &&
          (element.getParent() instanceof PyParameter && pySettings.SPACE_AROUND_EQ_IN_NAMED_PARAMETER ||
           element.getParent() instanceof PyKeywordArgument && pySettings.SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT ||
           element.getParent() instanceof PyKeywordPattern && pySettings.SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT)) {
        return true;
      }
    }
    // W191 (indentation contains tabs) is reported also for indents inside multiline string literals,
    // thus underlying PSI element is not necessarily a whitespace
    if (problem.myCode.equals("W191") && CodeStyle.getIndentOptions(file).USE_TAB_CHARACTER) {
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
      LOG.info("Failed to parse problem line from pycodestyle.py: " + s);
    }
    return null;
  }

  private static class IgnoreErrorFix implements IntentionAction {
    private final String myCode;

    IgnoreErrorFix(String code) {
      myCode = code;
    }

    @NotNull
    @Override
    public String getText() {
      return PyPsiBundle.message("ANN.ignore.errors.like.this");
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
      InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, it -> {
        PyPep8Inspection tool = (PyPep8Inspection)it.getUnwrappedTool(PyPep8Inspection.INSPECTION_SHORT_NAME, file);
        if (!tool.ignoredErrors.contains(myCode)) {
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
