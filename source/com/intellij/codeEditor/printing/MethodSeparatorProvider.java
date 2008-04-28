/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.codeEditor.printing;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

import java.util.*;

public class MethodSeparatorProvider extends FileSeparatorProvider {
  @Override
  public List<LineMarkerInfo> getFileSeparators(final PsiFile file, final Document document) {
    final List<LineMarkerInfo> result = new ArrayList<LineMarkerInfo>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        LineMarkersPass action = new LineMarkersPass(file.getProject(), file, document, 0, file.getTextLength(), true);
        Collection<LineMarkerInfo> lineMarkerInfos = action.queryLineMarkers();
        for (LineMarkerInfo lineMarkerInfo : lineMarkerInfos) {
          if (lineMarkerInfo.separatorColor != null) {
            result.add(lineMarkerInfo);
          }
        }
      }
    });

    Collections.sort(result, new Comparator<LineMarkerInfo>() {
      public int compare(final LineMarkerInfo i1, final LineMarkerInfo i2) {
        return i1.startOffset - i2.startOffset;
      }
    });
    return result;
  }
}