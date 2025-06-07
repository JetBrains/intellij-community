// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.mapper;

import com.intellij.ide.vfs.VirtualFileId;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import com.intellij.ide.vfs.VirtualFileIdKt;

/**
 * Mapper class for converting a MarkdownHeader into a MarkdownHeaderInfo.
 */
public final class MarkdownHeaderMapper {
  private static final Logger logger = Logger.getInstance(MarkdownHeaderMapper.class);

  public static MarkdownHeaderInfo map(MarkdownHeader header) {
    if (header == null) {
      logger.warn("Header is null, returning null");
      return null;
    }
    String headerText = header.getText();

    PsiFile containingFile = header.getContainingFile();
    if (containingFile == null) {
      throw new IllegalArgumentException("Containing file not found for header: " + headerText);
    }
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) {
      throw new IllegalArgumentException("Virtual file not found for header: " + headerText);
    }

    String filePath = virtualFile.getPath();
    String fileName = virtualFile.getName();
    int textOffset = header.getTextOffset();

    int lineNumber = 0;
    var document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document != null) {
      lineNumber = document.getLineNumber(textOffset) + 1;
    }

    VirtualFileId virtualFileId = VirtualFileIdKt.rpcId(header.getContainingFile().getVirtualFile());

    return new MarkdownHeaderInfo(headerText, filePath, fileName, lineNumber, textOffset, virtualFileId);
  }
}

