package com.jetbrains.python.validation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.inspections.quickfix.ReformatFix;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class Pep8ExternalAnnotator extends ExternalAnnotator<Pep8ExternalAnnotator.State, Pep8ExternalAnnotator.State> {
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
    public final List<Problem> problems = new ArrayList<Problem>();

    public State(String interpreterPath, String fileText, HighlightDisplayLevel level) {
      this.interpreterPath = interpreterPath;
      this.fileText = fileText;
      this.level = level;
    }
  }

  @Nullable
  @Override
  public State collectionInformation(@NotNull PsiFile file) {
    final Sdk sdk = PythonSdkType.findPythonSdk(ModuleUtilCore.findModuleForPsiElement(file));
    if (sdk == null) return null;
    final String homePath = sdk.getHomePath();
    if (homePath == null) return null;
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getInspectionProfile();
    final HighlightDisplayKey key = HighlightDisplayKey.find("PyPep8Inspection");
    if (!profile.isToolEnabled(key)) {
      return null;
    }
    return new State(homePath, file.getText(), profile.getErrorLevel(key, file));
  }

  @Nullable
  @Override
  public State doAnnotate(State collectedInfo) {
    if (collectedInfo == null) return null;
    final String pep8Path = PythonHelpersLocator.getHelperPath("pep8.py");
    ProcessOutput output = PySdkUtil.getProcessOutput(new File(collectedInfo.interpreterPath).getParent(),
                                                      new String[]{
                                                        collectedInfo.interpreterPath,
                                                        pep8Path,
                                                        "-"
                                                      },
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
    return collectedInfo;
  }

  @Override
  public void apply(@NotNull PsiFile file, State annotationResult, @NotNull AnnotationHolder holder) {
    if (annotationResult == null) return;
    final String text = file.getText();
    for (Problem problem: annotationResult.problems) {
      int offset = StringUtil.lineColToOffset(text, problem.myLine-1, problem.myColumn-1);
      PsiElement problemElement = file.findElementAt(offset);
      if (!(problemElement instanceof PsiWhiteSpace) && !(problem.myCode.startsWith("E3"))) {
        final PsiElement elementAfter = file.findElementAt(offset + 1);
        if (elementAfter instanceof PsiWhiteSpace) {
          problemElement = elementAfter;
        }
      }
      if (problemElement != null) {
        final Annotation annotation;
        final String message = "PEP8: " + problem.myDescription;
        if (annotationResult.level == HighlightDisplayLevel.ERROR) {
          annotation = holder.createErrorAnnotation(problemElement, message);
        }
        else if (annotationResult.level == HighlightDisplayLevel.WARNING) {
          annotation = holder.createWarningAnnotation(problemElement, message);
        }
        else {
          annotation = holder.createWeakWarningAnnotation(problemElement, message);
        }
        annotation.registerUniversalFix(new ReformatFix(), null, null);
      }
    }
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
}
