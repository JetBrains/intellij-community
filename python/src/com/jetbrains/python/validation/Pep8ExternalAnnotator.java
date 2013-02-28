package com.jetbrains.python.validation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.CustomEditInspectionToolsSettingsAction;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.inspections.PyPep8Inspection;
import com.jetbrains.python.inspections.quickfix.ReformatFix;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
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
public class Pep8ExternalAnnotator extends ExternalAnnotator<Pep8ExternalAnnotator.State, Pep8ExternalAnnotator.State> {
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
    public final List<Problem> problems = new ArrayList<Problem>();

    public State(String interpreterPath, String fileText, HighlightDisplayLevel level,
                 List<String> ignoredErrors, int margin) {
      this.interpreterPath = interpreterPath;
      this.fileText = fileText;
      this.level = level;
      this.ignoredErrors = ignoredErrors;
      this.margin = margin;
    }
  }

  @Nullable
  @Override
  public State collectionInformation(@NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null || vFile.getFileType() != PythonFileType.INSTANCE) {
      return null;
    }
    Sdk sdk = PythonSdkType.findLocalCPython(ModuleUtilCore.findModuleForPsiElement(file));
    if (sdk == null) return null;
    final String homePath = sdk.getHomePath();
    if (homePath == null) return null;
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getInspectionProfile();
    final HighlightDisplayKey key = HighlightDisplayKey.find(PyPep8Inspection.INSPECTION_SHORT_NAME);
    if (!profile.isToolEnabled(key)) {
      return null;
    }
    final PyPep8Inspection inspection = profile.getUnwrappedTool(PyPep8Inspection.KEY, file);
    final List<String> ignoredErrors = inspection.ignoredErrors;
    final int margin = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings().RIGHT_MARGIN;
    return new State(homePath, file.getText(), profile.getErrorLevel(key, file), ignoredErrors, margin);
  }

  @Nullable
  @Override
  public State doAnnotate(State collectedInfo) {
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
                                                      null,
                                                      5000,
                                                      collectedInfo.fileText.getBytes());
    if (output.getStderrLines().isEmpty()) {
      for (String line : output.getStdoutLines()) {
        final Problem problem = parseProblem(line);
        if (problem != null) {
          collectedInfo.problems.add(problem);
        }
      }
    }
    else if (((ApplicationInfoImpl) ApplicationInfo.getInstance()).isEAP()) {
      LOG.info("Error running pep8.py: " + output.getStderr());
    }
    return collectedInfo;
  }

  @Override
  public void apply(@NotNull PsiFile file, State annotationResult, @NotNull AnnotationHolder holder) {
    if (annotationResult == null || !file.isValid()) return;
    final String text = file.getText();
    for (Problem problem : annotationResult.problems) {
      if (ignoreDueToSettings(file.getProject(), problem)) continue;
      int offset = StringUtil.lineColToOffset(text, problem.myLine - 1, problem.myColumn - 1);
      PsiElement problemElement = file.findElementAt(offset);
      if (!(problemElement instanceof PsiWhiteSpace) && !(problem.myCode.startsWith("E3"))) {
        final PsiElement elementAfter = file.findElementAt(offset + 1);
        if (elementAfter instanceof PsiWhiteSpace) {
          problemElement = elementAfter;
        }
      }
      if (problemElement != null) {
        TextRange problemRange = problemElement.getTextRange();
        if (StringUtil.offsetToLineNumber(text, problemRange.getStartOffset()) != StringUtil.offsetToLineNumber(text, problemRange.getEndOffset())) {
          problemRange = new TextRange(offset, StringUtil.lineColToOffset(text, problem.myLine, 0)-1);
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
        annotation.registerUniversalFix(new ReformatFix(), null, null);
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
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      profile.modifyToolSettings(PyPep8Inspection.KEY, file, new Consumer<PyPep8Inspection>() {
        @Override
        public void consume(PyPep8Inspection tool) {
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
