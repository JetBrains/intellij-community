/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretStateTransferableData;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

/**
 * @author Dennis.Ushakov
 */
public class HtmlCopyPastePreProcessor implements CopyPastePreProcessor {
  private static final Logger LOG = Logger.getInstance(HtmlCopyPastePreProcessor.class);

  private static final DataFlavor ourHtmlDataFlavor = buildDataFlavor("text/html;class=java.lang.String");
  private static final DataFlavor ourRtfDataFlavor = buildDataFlavor("text/rtf;class=java.io.InputStream");

  private static DataFlavor buildDataFlavor(final String flavor) {
    try {
      return new DataFlavor(flavor);
    }
    catch (ClassNotFoundException e) {
      return null;
    }
  }

  @Nullable
  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @NotNull
  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (file.getLanguage() != HTMLLanguage.INSTANCE) return text;

    CopyPasteManager manager = CopyPasteManager.getInstance();
    if (manager.areDataFlavorsAvailable(ourHtmlDataFlavor, ourRtfDataFlavor)) {
      Transferable content = manager.getContents();
      if (content != null && !content.isDataFlavorSupported(CaretStateTransferableData.FLAVOR)) {
        try {
          final String data = content.isDataFlavorSupported(ourHtmlDataFlavor) ? (String)content.getTransferData(ourHtmlDataFlavor) :
                              convertFromRtfStream((InputStream)content.getTransferData(ourRtfDataFlavor));
          if (!StringUtil.isEmpty(data)) return data;
        }
        catch (UnsupportedFlavorException e) {
          LOG.error(e);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return text;
  }

  private static String convertFromRtfStream(InputStream stream) {
    final DefaultStyledDocument document = new DefaultStyledDocument();
    try {
      new RTFEditorKit().read(stream, document, 0);
      final StringWriter writer = new StringWriter();
      new HTMLEditorKit().write(writer, document, 0, document.getLength());
      return writer.toString();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (BadLocationException e) {
      LOG.error(e);
    }
    return null;
  }
}
