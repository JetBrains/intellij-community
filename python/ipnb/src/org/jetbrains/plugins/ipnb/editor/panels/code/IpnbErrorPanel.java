package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbErrorOutputCell;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.List;

public class IpnbErrorPanel extends IpnbCodeOutputPanel<IpnbErrorOutputCell> {
  public IpnbErrorPanel(@NotNull final IpnbErrorOutputCell cell, @Nullable IpnbCodePanel ipnbCodePanel) {
    super(cell, null, ipnbCodePanel);
  }

  @Override
  protected JComponent createViewPanel() {
    final List<String> text = myCell.getText();
    return createColoredPanel(text);
  }

  @NotNull
  public static JComponent createColoredPanel(List<String> text) {
    if (text == null) return new JLabel();
    ColorPane ansiColoredPane = new ColorPane();
    final Font font = ansiColoredPane.getFont();
    final Font newFont = new Font(font.getName(), font.getStyle(), EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize());
    ansiColoredPane.setFont(newFont);
    ansiColoredPane.appendANSI(StringUtil.join(text, "\n"));
    ansiColoredPane.setBackground(IpnbEditorUtil.getBackground());
    ansiColoredPane.setEditable(false);
    return ansiColoredPane;
  }


  public static class ColorPane extends JTextPane {
    static final Color D_Red = Color.decode("#8B0000");
    static final Color D_Magenta = JBColor.MAGENTA;
    static final Color D_Green = Color.decode("#006400");
    static final Color D_Yellow = Color.decode("#A52A2A");
    static final Color D_Cyan = Color.decode("#5AB4EB");

    static final Color cReset = JBColor.BLACK;

    static Color currentColor = cReset;
    String remaining = "";

    public void append(Color color, String s) {
      StyleContext styleContext = StyleContext.getDefaultStyleContext();
      AttributeSet attributeSet = styleContext.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, color);
      int len = getDocument().getLength();
      setCaretPosition(len);
      setCharacterAttributes(attributeSet, false);
      replaceSelection(s);
    }

    public void appendANSI(String string) {
      int position = 0;
      int index;
      int mIndex;
      String substring;
      boolean continueSearch;
      String addString = remaining + string;
      remaining = "";

      if (addString.length() > 0) {
        if (handleCarriageReturn(addString)) {
          return;
        }
        index = addString.indexOf("\u001B");
        if (index == -1) {
          append(currentColor, addString);
          return;
        }

        if (index > 0) {
          substring = addString.substring(0, index);
          append(currentColor, substring);
          position = index;
        }
        continueSearch = true;
        while (continueSearch) {
          mIndex = addString.indexOf("m", position);
          if (mIndex < 0) {
            remaining = addString.substring(position);
            continueSearch = false;
            continue;
          }
          else {
            substring = addString.substring(position, mIndex + 1);
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            currentColor = getANSIColor(substring);
          }
          position = mIndex + 1;

          index = addString.indexOf("\u001B", position);

          if (index == -1) {
            substring = addString.substring(position);
            append(currentColor, substring);
            continueSearch = false;
            continue;
          }

          substring = addString.substring(position, index);
          position = index;
          append(currentColor, substring);
        }
      }
    }

    private boolean handleCarriageReturn(String addString) {
      int index = addString.indexOf("\r");
      if (index >= 0 && addString.length() > index+1) {
        appendANSI(addString.substring(0, index));
        String substring = addString.substring(index+1);
        final Element element = getDocument().getDefaultRootElement();
        final int position = getDocument().getLength();
        final int line = element.getElementIndex(position);
        final int offset = element.getElement(line).getStartOffset();
        try {
          getDocument().remove(offset, getDocument().getLength()-offset);
        }
        catch (BadLocationException ignored) {
        }
        appendANSI(substring);
        return true;
      }
      return false;
    }

    public static Color getANSIColor(String ANSIColor) {
      if (ANSIColor.equals("\u001B[30m") || ANSIColor.equals("\u001B[0;30m") || ANSIColor.equals("\u001B[1;30m")
          || ANSIColor.equals("\u001B[01;30m")) {
        return JBColor.BLACK;
      }
      else if (ANSIColor.equals("\u001B[31m") || ANSIColor.equals("\u001B[0;31m") || ANSIColor.equals("\u001B[1;31m")
               || ANSIColor.equals("\u001B[01;31m")) {
        return D_Red;
      }
      else if (ANSIColor.equals("\u001B[32m") || ANSIColor.equals("\u001B[0;32m") || ANSIColor.equals("\u001B[1;32m")
               || ANSIColor.equals("\u001B[01;32m")) {
        return D_Green;
      }
      else if (ANSIColor.equals("\u001B[33m") || ANSIColor.equals("\u001B[0;33m") || ANSIColor.equals("\u001B[1;33m")
               || ANSIColor.equals("\u001B[01;33m")) {
        return D_Yellow;
      }
      else if (ANSIColor.equals("\u001B[34m") || ANSIColor.equals("\u001B[0;34m") || ANSIColor.equals("\u001B[1;34m")
               || ANSIColor.equals("\u001B[01;34m")) {
        return JBColor.BLUE;
      }
      else if (ANSIColor.equals("\u001B[35m") || ANSIColor.equals("\u001B[0;35m") || ANSIColor.equals("\u001B[1;35m")
               || ANSIColor.equals("\u001B[01;35m")) {
        return D_Magenta;
      }
      else if (ANSIColor.equals("\u001B[36m") || ANSIColor.equals("\u001B[0;36m") || ANSIColor.equals("\u001B[1;36m")
               || ANSIColor.equals("\u001B[01;36m")) {
        return D_Cyan;
      }
      else if (ANSIColor.equals("\u001B[37m") || ANSIColor.equals("\u001B[0;37m") || ANSIColor.equals("\u001B[1;37m")
               || ANSIColor.equals("\u001B[01;37m")) {
        return JBColor.WHITE;
      }
      else if (ANSIColor.equals("\u001B[0m")) {
        return cReset;
      }
      else {
        return JBColor.BLACK;
      }
    }
  }
}
