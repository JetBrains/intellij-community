/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.net.IOExceptionDialog;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DownloadResourceFix implements LocalQuickFix {
  private final String myLocation;

  public DownloadResourceFix(String location) {
    myLocation = location;
  }

  @NotNull
  public String getName() {
    return "Download External Resource";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    boolean tryAgain = true;

    final DownloadManager.DownloadException[] ex = new DownloadManager.DownloadException[1];
    final Runnable runnable = () -> {
      final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      final DownloadManager downloadManager = new MyDownloadManager(project, progress);

      try {
        downloadManager.fetch(myLocation);
      }
      catch (DownloadManager.DownloadException e) {
        ex[0] = e;
      }
    };

    while (tryAgain) {
      tryAgain = false;
      if (ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, "Downloading Resource", true, project)) {
        if (ex[0] != null) {
          tryAgain = IOExceptionDialog.showErrorDialog("Error during Download", "Error downloading " + ex[0].getLocation());
          ex[0] = null;
        }
        else {
          break;
        }
      }
    }
  }

  private static class MyDownloadManager extends DownloadManager {
    public MyDownloadManager(Project project, ProgressIndicator progress) {
      super(project, progress);
    }

    private static void processReferences(XmlTag[] subTags, Set<String> list) {
      for (XmlTag xmlTag : subTags) {
        final String href = xmlTag.getAttributeValue("href");
        // TODO: Handle relative dependencies!
        if (href != null && href.startsWith("http://")) {
          list.add(href);
        }
      }
    }

    protected boolean isAccepted(PsiFile psiFile) {
      return XsltSupport.isXsltFile(psiFile);
    }

    protected Set<String> getResourceDependencies(PsiFile psiFile) {
      final XmlDocument document = ((XmlFile)psiFile).getDocument();
      if (document != null) {
        final XmlTag rootTag = document.getRootTag();
        if (rootTag != null) {
          final Set<String> list = new HashSet<>();
          processReferences(rootTag.findSubTags("include", XsltSupport.XSLT_NS), list);
          processReferences(rootTag.findSubTags("import", XsltSupport.XSLT_NS), list);
          return list;
        }
      }
      return Collections.emptySet();
    }
  }
}
