package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author max
 */
public class DescriptorComposer extends HTMLComposer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.DescriptorComposer");
  private DescriptorProviderInspection myTool;

  public DescriptorComposer(DescriptorProviderInspection tool) {
    myTool = tool;
  }

  public void compose(StringBuffer buf, RefEntity refEntity) {
    Project project = myTool.getManager().getProject();
    if (refEntity instanceof RefElement) {
      RefElement refElement = (RefElement) refEntity;

      genPageHeader(buf, refElement);

      if (myTool.getDescriptions(refElement) != null) {
        appendHeading(buf, "Problem synopsis");

        ProblemDescriptor[] descriptions = myTool.getDescriptions(refElement);

        startList();
        for (int i = 0; i < descriptions.length; i++) {
          final ProblemDescriptor description = descriptions[i];

          startListItem(buf);
          composeDescription(description, project, i, buf);
          doneListItem(buf);
        }

        doneList(buf);

        appendResolution(buf, myTool, refElement);
      } else {
        appendNoProblems(buf);
      }
    }
  }

  public void compose(StringBuffer buf, RefElement refElement, ProblemDescriptor descriptor) {
    Project project = myTool.getManager().getProject();
    ProblemDescriptor[] descriptions = myTool.getDescriptions(refElement);

    int problemIdx = -1;
    for (int i = 0; i < descriptions.length; i++) {
      ProblemDescriptor description = descriptions[i];
      if (description == descriptor) {
        problemIdx = i;
        break;
      }
    }
    if (problemIdx == -1) return;

    genPageHeader(buf, refElement);
    appendHeading(buf, "Problem synopsis");
    buf.append("<br>");
    appendAfterHeaderIndention(buf);

    composeDescription(descriptor, project, problemIdx, buf);
    final LocalQuickFix fix = descriptor.getFix();
    if (fix != null) {
      buf.append("<br><br>");
      appendHeading(buf, "Problem resolution");
      buf.append("<br>");
      appendAfterHeaderIndention(buf);

      buf.append("<font style=\"font-family:verdana;\"");
      buf.append("<a HREF=\"file://bred.txt#invokelocal:");
      buf.append("\">");
      buf.append(fix.getName());
      buf.append("</a></font>");
    }
  }

  private void composeDescription(final ProblemDescriptor description,
                                  Project project,
                                  int i,
                                  StringBuffer buf) {
    PsiElement expression = description.getPsiElement();
    StringBuffer anchor = new StringBuffer();
    if (expression != null) {
      VirtualFile vFile = expression.getContainingFile().getVirtualFile();

      anchor.append("<a HREF=\"");
      try {
        anchor.append(new URL(vFile.getUrl() + "#descr:" + i));
      } catch (MalformedURLException e) {
        LOG.error(e);
      }

      anchor.append("\">");
      anchor.append(expression.getText().replaceAll("\\$", "\\\\\\$"));
      anchor.append("</a>");
    }
    else {
      anchor.append("<font style=\"font-family:verdana; font-weight:bold; color:#FF0000\";>invalidated item</font>");
    }

    String descriptionTemplate = description.getDescriptionTemplate();
    if (descriptionTemplate != null) {
      String res = descriptionTemplate.replaceAll("#ref", anchor.toString());
      res = res.replaceAll("#loc", "at line " + description.getLineNumber());
      buf.append(res);
    }
    else {
      buf.append("No error message provided.");
    }
  }
}
