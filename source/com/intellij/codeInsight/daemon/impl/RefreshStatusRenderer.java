package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import java.awt.*;

public class RefreshStatusRenderer implements ErrorStripeRenderer {
  private static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/errorsInProgress.png");
  private static final Icon NO_ERRORS_ICON = IconLoader.getIcon("/general/errorsOK.png");
  private static final Icon ERRORS_FOUND_ICON = IconLoader.getIcon("/general/errorsFound.png");
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
    public int warningErrorCount;
    public int errorCount;
  }

  protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus() {
    DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
    if (myFile == null || !myHighlighter.isHighlightingAvailable(myFile)) return null;

    if (myHighlighter.isErrorAnalyzingFinished(myFile)) {
      status.errorAnalyzingFinished = true;
      HighlightInfo[] infos = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightInfo.WARNING, myProject);
      status.warningErrorCount = infos == null ? 0 : infos.length;
      infos = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightInfo.ERROR, myProject);
      status.errorCount = infos == null ? 0 : infos.length;

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
    if (status.errorAnalyzingFinished) {
      final boolean inspecting = !status.inspectionFinished;
      String text = "<html><body>";
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
      text += "</body></html>";
      return text;
    } 
    else {
      return "Analyzing for syntactic correctness.";
    }
  }

  public void paint(Component c, Graphics g, Rectangle r) {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus();

    Icon icon;
    if (status == null) {
      icon = NO_ICON;
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

      final boolean inspecting = !status.inspectionFinished;
      if (inspecting) {
        final LayeredIcon layeredIcon = new LayeredIcon(2);
        layeredIcon.setIcon(icon, 0);
        layeredIcon.setIcon(INSPECTION_ICON, 1);
        icon = layeredIcon;
      }
    }

    int height = icon.getIconHeight();
    int width = icon.getIconWidth();
    icon.paintIcon(c, g, r.x + (r.width - width) / 2, r.y + (r.height - height) / 2);
  }
}