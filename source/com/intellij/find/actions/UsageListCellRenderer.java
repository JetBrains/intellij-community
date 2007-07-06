/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 5, 2007
 * Time: 7:32:49 PM
 */
package com.intellij.find.actions;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.rules.UsageInFile;

import javax.swing.*;

public class UsageListCellRenderer extends ColoredListCellRenderer {
  private static final EditorColorsScheme ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
  private static final SimpleTextAttributes ourReadOnlyAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.READONLY_PREFIX));

  protected void customizeCellRenderer(final JList list,
                                       final Object value,
                                       final int index,
                                       final boolean selected,
                                       final boolean hasFocus) {
    Usage usage = (Usage)value;
    UsagePresentation presentation = usage.getPresentation();
    setIcon(presentation.getIcon());
    VirtualFile virtualFile = getVirtualFile(usage);
    if (virtualFile != null) {
      append(virtualFile.getName() + ": ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      setIcon(FileTypeManager.getInstance().getFileTypeByFile(virtualFile).getIcon());
    }
    if (usage.isReadOnly()) {
      append(UsageViewBundle.message("node.readonly") + " ", ourReadOnlyAttributes);
    }

    TextChunk[] text = presentation.getText();
    for (TextChunk textChunk : text) {
      append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
    }
  }

  public static VirtualFile getVirtualFile(final Usage usage) {
    return usage instanceof UsageInFile ? ((UsageInFile)usage).getFile() : null;
  }
}