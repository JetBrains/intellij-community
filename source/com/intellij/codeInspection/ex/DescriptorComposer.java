package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.text.CharArrayUtil;

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
    if (refEntity instanceof RefElement) {
      RefElement refElement = (RefElement)refEntity;

      genPageHeader(buf, refElement);

      if (myTool.getDescriptions(refElement) != null) {
        appendHeading(buf, "Problem synopsis");

        ProblemDescriptor[] descriptions = myTool.getDescriptions(refElement);

        startList();
        for (int i = 0; i < descriptions.length; i++) {
          final ProblemDescriptor description = descriptions[i];

          startListItem(buf);
          composeDescription(description, i, buf);
          doneListItem(buf);
        }

        doneList(buf);

        appendResolution(buf, myTool, refElement);
      }
      else {
        appendNoProblems(buf);
      }
    }
  }

  public void compose(StringBuffer buf, RefElement refElement, ProblemDescriptor descriptor) {
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

    composeDescription(descriptor, problemIdx, buf);
    final LocalQuickFix[] fixes = descriptor.getFixes();
    if (fixes != null) {
      buf.append("<br><br>");
      appendHeading(buf, "Problem resolution");
      buf.append("<br>");
      appendAfterHeaderIndention(buf);

      int idx = 0;
      for (LocalQuickFix fix : fixes) {
        buf.append("<font style=\"font-family:verdana;\"");
        buf.append("<a HREF=\"file://bred.txt#invokelocal:" + (idx++));
        buf.append("\">");
        buf.append(fix.getName());
        buf.append("</a></font>");
        buf.append("<br>");
        appendAfterHeaderIndention(buf);
      }
    }
  }

  private void composeDescription(final ProblemDescriptor description, int i, StringBuffer buf) {
    PsiElement expression = description.getPsiElement();
    StringBuffer anchor = new StringBuffer();
    if (expression != null) {
      VirtualFile vFile = expression.getContainingFile().getVirtualFile();

      anchor.append("<a HREF=\"");
      try {
        anchor.append(new URL(vFile.getUrl() + "#descr:" + i));
      }
      catch (MalformedURLException e) {
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
      final int lineNumber = description.getLineNumber();
      StringBuffer lineAnchor = new StringBuffer();
      if (expression != null && lineNumber > 0) {
        VirtualFile vFile = expression.getContainingFile().getVirtualFile();
        Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        lineAnchor.append("at line ");
        lineAnchor.append("<a HREF=\"");
        try {
          int offset = doc.getLineStartOffset(lineNumber - 1);
          offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), offset, " \t");
          lineAnchor.append(new URL(vFile.getUrl() + "#" + offset));
        }
        catch (MalformedURLException e) {
          LOG.error(e);
        }
        lineAnchor.append("\">");
        lineAnchor.append(Integer.toString(lineNumber));
        lineAnchor.append("</a>");
        res = res.replaceAll("#loc", lineAnchor.toString());
      }
      buf.append(res);
    }
    else {
      buf.append("No error message provided.");
    }
  }
}
