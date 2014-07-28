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
package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.MarkdownCell;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class MarkdownPanel extends IpnbPanel {
  private static final Logger LOG = Logger.getInstance(MarkdownPanel.class);
  private boolean myEditing = false;
  private Project myProject;

  public MarkdownPanel(Project project, MarkdownCell cell) {
    super();
    setLayout(new VerticalFlowLayout(FlowLayout.LEFT));
    myProject = project;
    initPanel(cell.getSource());

  }

  private void initPanel(@Nullable final String[] text) {
    if (text == null) return;
    StringBuilder formula = new StringBuilder();
    boolean hasFormula = false;
    boolean isEscaped = false;
    boolean inFormula = false;
    for (String string : text) {
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
            add(picLabel);
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
          final JLabel comp = new JLabel("<html><body style='width: 900px'" + string + "</body></html>");
          final Font font = new Font(Font.SERIF, Font.PLAIN, 16);
          comp.setFont(font);
          add(comp);
        }
      }
    }
    if (hasFormula) {
      try {
        TeXFormula f = new TeXFormula(formula.toString());
        final TeXIcon icon = f.createTeXIcon(TeXFormula.SERIF, 20);
        JLabel picLabel = new JLabel(icon);
        add(picLabel);
      }
      catch (ParseException x) {
        LOG.error("Error parsing " + formula.toString() + " because of:" + x.getMessage());
      }
    }
    setBackground(IpnbEditorUtil.getBackground());
    setOpaque(true);

  }

  public boolean isEditing() {
    return myEditing;
  }

  public void setEditing(boolean isEditing) {
    myEditing = isEditing;
    updatePanel();
  }

  private void updatePanel() {
    removeAll();
  }
}
