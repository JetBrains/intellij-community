package org.jetbrains.plugins.ruby.testing.sm;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class FileUrlProvider implements LocationProvider{
  private static final Logger LOG = Logger.getInstance(FileUrlProvider.class.getName());

  @NonNls private static final String FILE_PROTOCOL_ID = "file";

  @NotNull
  public List<Location> getLocation(@NotNull final String protocolId, @NotNull final String path,
                                    final Project project) {

    if (!FILE_PROTOCOL_ID.equals(protocolId)) {
      return Collections.emptyList();
    }

    final String normalizedPath = path.replace(File.separatorChar, '/');

    final int lineNoSeparatorIndex = normalizedPath.lastIndexOf(':');

    final String filePath;
    final int lineNumber;
    // if line is specified
    if (lineNoSeparatorIndex >= 0) {
      final String lineNumStr = normalizedPath.substring(lineNoSeparatorIndex + 1);
      int lineNum = 0;
      try {
        lineNum = Integer.parseInt(lineNumStr);
      } catch (NumberFormatException e) {
        LOG.warn(protocolId + ": Malformed location path: " + path, e);
      }

      filePath = normalizedPath.substring(0, lineNoSeparatorIndex);
      lineNumber = lineNum;
    } else {
      // unknown line
      lineNumber = 1;
      filePath = normalizedPath;
    }
    //Now we should seach file with most sutable path
    final List<VirtualFile> virtualFiles = LocationProviderUtil.findSuitableFilesFor(filePath);
    if (virtualFiles.isEmpty()) {
      return Collections.emptyList();
    }

    final List<Location> locations = new ArrayList<Location>(2);
    for (VirtualFile file : virtualFiles) {
      locations.add(createLocationFor(project, file, lineNumber));
    }
    return locations;
  }

  @Nullable
  protected static Location createLocationFor(final Project project,
                                            @NotNull final VirtualFile virtualFile, final int lineNum) {
    assert lineNum > 0;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile == null) {
      return null;
    }

    final Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (doc == null) {
      return null;
    }

    final int lineStartOffset = doc.getLineStartOffset(lineNum - 1);

    PsiElement elementAtLine = psiFile.findElementAt(lineStartOffset);
    if (elementAtLine != null) {
      // skip whitespaces
      while (elementAtLine instanceof PsiWhiteSpace) {
        elementAtLine = elementAtLine.getNextSibling();
      }
    }
    return PsiLocation.fromPsiElement(project, elementAtLine != null ? elementAtLine : psiFile);
  }
}
