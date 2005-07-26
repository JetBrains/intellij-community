package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.ide.IconUtilEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;

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
    String text = "<html><body>";
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      text += "No analysis have been performed.";
      text += "</body></html>";
      return text;
    }
    else if (status.errorAnalyzingFinished) {
      boolean inspecting = !status.inspectionFinished;
      text += inspecting ? "Performing code inspection." : "Analysis completed.";
      if (status.warningErrorCount == 0) {
        text += "<br>No errors or warnings found";
        if (inspecting) {
          text += " so far";
        }
        text += ".";
      }
      else {
        if (status.errorCount != 0) {
          text += "<br><font color=red><b>" + status.errorCount + "</b></font> error" + (status.errorCount == 1 ? "":"s") +" found";
          if (inspecting) {
            text += " so far";
          }
          text += ".";
        }
        int warnings = status.warningErrorCount - status.errorCount;
        if (warnings != 0) {
          text += "<br><b>" + warnings + "</b> warning" + (warnings == 1 ? "":"s") +" found";
          if (inspecting) {
            text += " so far";
          }
          text += ".";
        }
      }

      text += getMessageByRoots(status.noHighlightingRoots, status.rootsNumber, "No syntax highlighting performed");
      text += getMessageByRoots(status.noInspectionRoots, status.rootsNumber, "No inspections performed");
      text += "</body></html>";
      return text;
    }
    else {
      return "Analyzing for syntactic correctness.";
    }
  }

  private String getMessageByRoots(String [] roots, int rootsNumber, String prefix){
    String text = "";
    if (roots != null) {
      final int length = roots.length;
      if (length > 0){
        text += "<br>" + prefix;
        if (rootsNumber > 1){
          text += " for ";
          for (int i = 0; i < length - 1; i++) {
            text += roots[i] + ", ";
          }
          text += roots[length - 1];
        }
        text += ".";
      }
    }
    return text;
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
