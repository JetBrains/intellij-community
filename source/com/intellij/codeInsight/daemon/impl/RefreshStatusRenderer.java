package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.ide.IconUtilEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class RefreshStatusRenderer implements ErrorStripeRenderer {
  private static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/errorsInProgress.png");
  private static final Icon NO_ERRORS_ICON = IconLoader.getIcon("/general/errorsOK.png");
  private static final Icon ERRORS_FOUND_ICON = IconLoader.getIcon("/general/errorsFound.png");
  private static final Icon NO_ANALYSIS_ICON = IconLoader.getIcon("/general/noAnalysis.png");
  private static final Icon WARNINGS_FOUND_ICON = IconLoader.getIcon("/general/warningsFound.png");
  private static final Icon INSPECTION_ICON = IconLoader.getIcon("/general/inspectionInProgress.png");
  private static final Icon NO_ICON = EmptyIcon.create(IN_PROGRESS_ICON.getIconWidth(), IN_PROGRESS_ICON.getIconHeight());

  private Project myProject;
  private Document myDocument;
  private final PsiFile myFile;
  private DaemonCodeAnalyzerImpl myHighlighter;

  @NonNls private static final String HTML_HEADER = "<html><body>";
  @NonNls private static final String HTML_FOOTER = "</body></html>";
  @NonNls private static final String BR = "<br>";
  @NonNls private static final String NO_PASS_FOR_MESSAGE_KEY_SUFFIX = ".for";

  public static class DaemonCodeAnalyzerStatus {
    public boolean errorAnalyzingFinished;
    public boolean inspectionFinished;
    public String [] noHighlightingRoots;
    public int warningErrorCount;
    public int errorCount;
    public String [] noInspectionRoots;
    public int rootsNumber;
  }

  protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus() {
    DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
    if (myFile == null || !myHighlighter.isHighlightingAvailable(myFile)) return null;

    ArrayList<String> noInspectionRoots = new ArrayList<String>();
    ArrayList<String> noHighlightingRoots = new ArrayList<String>();
    final PsiFile[] roots = myFile.getPsiRoots();
    for (PsiFile file : roots) {
      if (!HighlightUtil.isRootHighlighted(file)){
        noHighlightingRoots.add(file.getLanguage().getID());
      } else if (!HighlightUtil.isRootInspected(file)){
        noInspectionRoots.add(file.getLanguage().getID());
      }
    }
    status.noInspectionRoots = noInspectionRoots.isEmpty() ? null : noInspectionRoots.toArray(new String[noInspectionRoots.size()]);
    status.noHighlightingRoots = noHighlightingRoots.isEmpty() ? null : noHighlightingRoots.toArray(new String[noHighlightingRoots.size()]);
    status.rootsNumber = roots.length;

    if (myHighlighter.isErrorAnalyzingFinished(myFile)) {
      status.errorAnalyzingFinished = true;
      HighlightInfo[] infos = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.WARNING, myProject);
      status.warningErrorCount = infos.length;
      infos = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.ERROR, myProject);
      status.errorCount = infos.length;

      status.inspectionFinished = myHighlighter.isInspectionCompleted(myFile);
    }
    return status;
  }

  public RefreshStatusRenderer(Project project, DaemonCodeAnalyzerImpl highlighter, Document document, PsiFile file) {
    myProject = project;
    myHighlighter = highlighter;
    myDocument = document;
    myFile = file;
  }

  public String getTooltipMessage() {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus();

    if (status == null) return null;
    String text = HTML_HEADER;
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      text += DaemonBundle.message("analysis.havent.been.run");
      text += HTML_FOOTER;
      return text;
    }
    else if (status.errorAnalyzingFinished) {
      boolean inspecting = !status.inspectionFinished;
      text += inspecting ? DaemonBundle.message("pass.inspection") : DaemonBundle.message("analysis.completed");
      if (status.warningErrorCount == 0) {
        text += BR;
        if (inspecting) {
          text += DaemonBundle.message("no.errors.or.warnings.found.so.far");
        }
        else {
          text += DaemonBundle.message("no.errors.or.warnings.found");
        }
      }
      else {
        if (status.errorCount != 0) {
          text += BR;
          text += DaemonBundle.message(inspecting ? "errors.count.so.far" : "errors.count", status.errorCount);
        }

        int warnings = status.warningErrorCount - status.errorCount;
        if (warnings != 0) {
          text += BR;
          text += DaemonBundle.message(inspecting ? "warnings.count.so.far" : "warnings.count", warnings);
        }
      }

      text += getMessageByRoots(status.noHighlightingRoots, status.rootsNumber, "no.syntax.highlighting.performed");
      text += getMessageByRoots(status.noInspectionRoots, status.rootsNumber, "no.inspections.performed");
      text += HTML_FOOTER;
      return text;
    }
    else {
      return DaemonBundle.message("pass.syntax");
    }
  }

  private String getMessageByRoots(String [] roots, int rootsNumber, @NonNls String prefix){
    if (roots != null) {
      final int length = roots.length;
      if (length > 0){
        if (rootsNumber > 1){
          return BR + DaemonBundle.message(prefix + NO_PASS_FOR_MESSAGE_KEY_SUFFIX, StringUtil.join(roots, ", "));
        }
        else {
          return BR + DaemonBundle.message(prefix);
        }
      }
    }
    return "";
  }

  public void paint(Component c, Graphics g, Rectangle r) {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus();

    Icon icon;
    if (status == null) {
      icon = NO_ICON;
    } else if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber){
      icon = NO_ANALYSIS_ICON;
    }
    else if (!status.errorAnalyzingFinished) {
      icon = IN_PROGRESS_ICON;
    }
    else {
      if (status.warningErrorCount == 0) {
        icon = NO_ERRORS_ICON;
      }
      else if (status.errorCount != 0) {
        icon = ERRORS_FOUND_ICON;
      }
      else {
        icon = WARNINGS_FOUND_ICON;
      }

      boolean inspecting = !status.inspectionFinished;
      if (inspecting) {
        icon = IconUtilEx.createLayeredIcon(icon, INSPECTION_ICON);
      }
    }

    int height = icon.getIconHeight();
    int width = icon.getIconWidth();
    icon.paintIcon(c, g, r.x + (r.width - width) / 2, r.y + (r.height - height) / 2);
  }
}
