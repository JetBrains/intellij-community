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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import net.sourceforge.jeuclid.swing.JMathComponent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.MarkdownCell;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;
import uk.ac.ed.ph.snuggletex.XMLStringOutputOptions;
import uk.ac.ed.ph.snuggletex.internal.util.XMLUtilities;
import uk.ac.ed.ph.snuggletex.utilities.SimpleStylesheetCache;
import uk.ac.ed.ph.snuggletex.utilities.StylesheetManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

/**
 * @author traff
 */
public class MarkdownPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(MarkdownPanel.class);
  private boolean myEditing = false;
  private Project myProject;

  public MarkdownPanel(Project project, MarkdownCell cell) {
    super(new BorderLayout());
    myProject = project;
    final String text = StringUtil.join(cell.getSource(), "\n");
    initPanel(text);


    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
//          setEditing(true); TODO: replace panel with editable textarea
          throw new IllegalStateException("not supported");
        }
      }
    });
  }

  private void initPanel(@Nullable  String text) {
    if (text == null) return;
    setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridy = 0;
    c.gridx = 0;

    final SnuggleEngine engine = new SnuggleEngine(new SimpleStylesheetCache());
    final SnuggleSession session = engine.createSession();

    final SnuggleInput input = new SnuggleInput(text);
    try {
      session.parseInput(input);
    }
    catch (IOException e) {
      LOG.error("Couldn't parse formula " + text);
      return;
    }
    final NodeList nodes = session.buildDOMSubtree();
    for (int i = 0; i != nodes.getLength(); ++i) {
      final Node node = nodes.item(i);
      final StylesheetManager stylesheetManager = session.getStylesheetManager();
      final XMLStringOutputOptions xmlStringOutputOptions = engine.getDefaultXMLStringOutputOptions();
      final String str = XMLUtilities.serializeNodeChildren(stylesheetManager, node, xmlStringOutputOptions);

      if (str.startsWith("<math")) {
        final JMathComponent comp = new JMathComponent();
        comp.setBackground(IpnbEditorUtil.getBackground());
        comp.setOpaque(true);
        comp.setContent("<html>" + str + "</html>");
        add(comp, c);
      }
      else {
        final String html = IpnbUtils.markdown2Html(str);
        final JBLabel comp = new JBLabel(html);
        comp.setBackground(IpnbEditorUtil.getBackground());
        comp.setOpaque(true);
        add(comp, c);
      }
      c.gridy = c.gridy + 1;
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
    if (myEditing) {


    }
  }
}
