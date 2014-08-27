package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import javax.swing.*;
import java.awt.*;

public class IpnbMarkdownPanel extends IpnbEditablePanel<JPanel, IpnbMarkdownCell> {
  private static final Logger LOG = Logger.getInstance(IpnbMarkdownPanel.class);

  public IpnbMarkdownPanel(@NotNull final IpnbMarkdownCell cell) {
    super(cell);
    initPanel();
  }

  @Override
  protected String getRawCellText() {
    final String string = myCell.getSourceAsString();
    if (isStyleOrScript(string)) {
      return "";
    }
    return string;
  }

  private boolean isStyleOrScript(String string) {
    return string.contains("<style>") || string.contains("<script>");
  }

  @Override
  protected JPanel createViewPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(FlowLayout.LEFT, false, true));
    updatePanel(panel);
    panel.setBackground(IpnbEditorUtil.getBackground());
    panel.setOpaque(true);
    return panel;
  }

  private void updatePanel(@NotNull final JPanel panel) {
    panel.removeAll();
    StringBuilder formula = new StringBuilder();
    boolean hasFormula = false;
    boolean isEscaped = false;
    boolean inFormula = false;
    if (isStyleOrScript(myCell.getSourceAsString())) return;
    for (String string : myCell.getSource()) {
      string = StringUtil.replace(string, "\\(", "(");
      string = StringUtil.replace(string, "\\)", ")");
      if (string.startsWith("```") && !isEscaped) {
        isEscaped = true;
      }
      else if (StringUtil.trimTrailing(string).endsWith("```") && isEscaped) {
        isEscaped = false;
      }

      string = string.replace("\n", " \n");
      if ((StringUtil.trimTrailing(string).endsWith("$$") || string.startsWith("\\\\end{")) && inFormula) {
        inFormula = false;
        string = StringUtil.trimTrailing(string);
        if (string.endsWith("$$")) {
          string = StringUtil.trimEnd(string, "$$");
        }
        formula.append(string);
      }
      else if (string.trim().startsWith("$$") && !isEscaped) {
        string = string.substring(2);
        formula.append(string);
        hasFormula = true;
        inFormula = true;
      }
      else if (string.startsWith("\\") && !isEscaped || inFormula) {
        inFormula = true;
        hasFormula = true;
        if (string.contains("equation*"))
          string = string.replace("equation*", "align");
        formula.append(string);
      }
      else {
        if (hasFormula) {
          try {
            TeXFormula f = new TeXFormula(formula.toString());
            final Image image = f.createBufferedImage(TeXFormula.SERIF, new Float(20.), JBColor.BLACK, JBColor.WHITE);
            JLabel picLabel = new JLabel(new ImageIcon(image));
            panel.add(picLabel);
          }
          catch (ParseException x) {
            LOG.error("Error parsing " + formula.toString() + " because of:" + x.getMessage());
          }
          hasFormula = false;
          formula = new StringBuilder();

        }
        else {
          string = StringUtil.trimStart(string, "```");
          string = StringUtil.trimEnd(string, "```");
          if (!isEscaped)
            string = IpnbUtils.markdown2Html(string);
          else
            string = "<p>"+string+"</p>";
          final JLabel comp = new JLabel("<html><body style='width: " + IpnbEditorUtil.PANEL_WIDTH + "px'>" + string + "</body></html>");
          final Font font = new Font(Font.SERIF, Font.PLAIN, 16);
          comp.setFont(font);
          panel.add(comp);
        }
      }
    }
    if (hasFormula) {
      try {
        TeXFormula f = new TeXFormula(formula.toString());
        final TeXIcon icon = f.createTeXIcon(TeXFormula.SERIF, 20);
        JLabel picLabel = new JLabel(icon);
        panel.add(picLabel);
      }
      catch (ParseException x) {
        LOG.error("Error parsing " + formula.toString() + " because of:" + x.getMessage());
      }
    }
  }

  @Override
  public void updateCellView() {
    final String text = myEditablePanel.getText();
    myCell.setSource(StringUtil.splitByLines(text));
    updatePanel(myViewPanel);
  }

}
