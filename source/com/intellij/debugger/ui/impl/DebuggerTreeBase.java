/*
 * Class DebuggerTreeBase
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;


public class DebuggerTreeBase extends Tree {
  private Project myProject;
  private DebuggerTreeNodeImpl myCurrentTooltipNode;
  private JComponent myCurrentTooltip;
  protected final TipManager myTipManager;

  public DebuggerTreeBase(TreeModel model, Project project) {
    super(model);
    myProject = project;
    putClientProperty("JTree.lineStyle", "Angled");
    setRootVisible(false);
    setShowsRootHandles(true);
    setCellRenderer(new DebuggerTreeRenderer());
    updateUI();
    myTipManager = new TipManager(this, new TipManager.TipFactory() {
          public JComponent createToolTip(MouseEvent e) {
            return DebuggerTreeBase.this.createToolTip(e);
          }
        });
    TreeUtil.installActions(this);
  }

  private int getMaximumChars(String s, FontMetrics metrics, int maxWidth) {
    int minChar = 0;
    int maxChar = s.length();
    int chars;
    while(minChar < maxChar) {
      chars = (minChar + maxChar + 1) / 2;
      int width = metrics.stringWidth(s.substring(0,  chars));
      if(width <= maxWidth) {
        minChar = chars;
      } else {
        maxChar = chars - 1;
      }
    }
    return minChar;
  }

  private JComponent createTipContent(String tipText) {
    JToolTip tooltip = new JToolTip();

    if(tipText == null) {
      tooltip.setTipText(tipText);
    } else {
      Dimension rootSize = getVisibleRect().getSize();
      Insets borderInsets = tooltip.getBorder().getBorderInsets(tooltip);
      rootSize.width -= (borderInsets.left + borderInsets.right) * 2;
      rootSize.height -= (borderInsets.top + borderInsets.bottom) * 2;

      StringBuffer tipBuffer = new StringBuffer(tipText.length());
      StringTokenizer tokenizer = new StringTokenizer(tipText, "\n");
      while(tokenizer.hasMoreElements()) {
        String line = tokenizer.nextToken();
        for (;;) {
          int maximumChars = getMaximumChars(line, tooltip.getFontMetrics(tooltip.getFont()), rootSize.width);
          if(maximumChars == line.length()) {
            tipBuffer.append(line.substring(0, maximumChars));
            tipBuffer.append('\n');
            break;
          } else {

            int chars;
            for(chars = maximumChars - 2; chars > 1; chars --) {
              if(getMaximumChars(line.substring(0, chars), tooltip.getFontMetrics(tooltip.getFont()), rootSize.width) < maximumChars) break;
            }
            tipBuffer.append(line.substring(0, chars));
            tipBuffer.append('\\');
            tipBuffer.append('\n');
            line = line.substring(maximumChars - 2);
          }
        }
      }

      Element html = new Element("html");

      String text = tipBuffer.toString();

/*      int lastLine = 0;
      for (int i = 0; i < text.length(); i++) {
        if(text.charAt(i) == '\n') {
//          Element p = new Element("p");
          Element pre = new Element("pre");
//          p.addContent(pre);
          html.addContent(pre);
          pre.setText(text.substring(lastLine, i));
          lastLine = i + 1;
        }
      }
//      Element p = new Element("p");
      Element pre = new Element("pre");
//      p.addContent(pre);
      html.addContent(pre);
      pre.setText(text.substring(lastLine, text.length()));*/

      Element p = new Element("pre");
      html.addContent(p);
      p.setText(text);

      XMLOutputter outputter = JDOMUtil.createOutputter("\n");
      outputter.setExpandEmptyElements(true);
      outputter.setNewlines(true);
      outputter.setTextTrim(false);
      outputter.setTrimAllWhite(false);
      outputter.setLineSeparator("\n");
      outputter.setTextNormalize(false);
      tooltip.setTipText(outputter.outputString(html));
    }

    tooltip.setBorder(null);

    return tooltip;
  }

  public JComponent createToolTip(MouseEvent e) {
    DebuggerTreeNodeImpl node = getNodeToShowTip(e);
    if (node == null) return null;

    if(myCurrentTooltip != null &&
       myCurrentTooltip.isShowing() &&
       myCurrentTooltipNode == node) return myCurrentTooltip;
    
    myCurrentTooltipNode = node;

    JToolTip toolTip = new JToolTip();
    toolTip.setLayout(new BorderLayout());

    String toolTipText = getTipText(node);
    if(toolTipText == null) return null;

    JScrollPane scrollPane = new JScrollPane(createTipContent(toolTipText));
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    toolTip.add(scrollPane);

    JComponent tipContent = createTipContent(toolTipText);
    Rectangle tipRectangle = getTipRectangle(e, tipContent.getPreferredSize());
    if(toolTip.getBorder() != null) {
      Insets borderInsets = toolTip.getBorder().getBorderInsets(this);
      tipRectangle.setSize(tipRectangle.width  + borderInsets.left + borderInsets.right,
                           tipRectangle.height + borderInsets.top  + borderInsets.bottom);
    }
    Dimension tipSize = new Dimension(tipRectangle.getSize());

    if(tipRectangle.getWidth() < tipContent.getPreferredSize().getWidth()) {
      tipSize.height += scrollPane.getHorizontalScrollBar().getPreferredSize().height;
    }

    if(tipRectangle.getHeight() < tipContent.getPreferredSize().getHeight()) {
      tipSize.width += scrollPane.getVerticalScrollBar().getPreferredSize().width;
    }

    if(!tipSize.equals(tipRectangle.getSize())) {
      tipRectangle = getTipRectangle(e, tipSize);
    }

    toolTip.setPreferredSize(tipRectangle.getSize());
    toolTip.setLocation(tipRectangle.getLocation());
    myCurrentTooltip = toolTip;

    return toolTip;
  }

  private String getTipText(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    if (descriptor instanceof ValueDescriptorImpl) {
      String text = ((ValueDescriptorImpl)descriptor).getLabel();
      if (text != null) {
        if(StringUtil.startsWithChar(text, '{') && text.indexOf('}') > 0) {
          int idx = text.indexOf('}');
          if(idx != text.length() - 1) {
            text = text.substring(idx + 1);
          }
        }

        if(StringUtil.startsWithChar(text, '\"') && StringUtil.endsWithChar(text, '\"')) text = text.substring(1, text.length() - 1);

        if (text.length() > 0 && (text.indexOf('\n') >= 0 || !getVisibleRect().contains(getRowBounds(getRowForPath(new TreePath(node.getPath())))))) {
          String tipText = prepareToolTipText(text);
          return tipText;
        }
      }
    }
    return null;
  }

  private DebuggerTreeNodeImpl getNodeToShowTip(MouseEvent event) {
    TreePath path = getPathForLocation(event.getX(), event.getY());
    if (path != null) {
      Object last = path.getLastPathComponent();
      if (last instanceof DebuggerTreeNodeImpl) return (DebuggerTreeNodeImpl)last;
    }

    return null;
  }

  private Rectangle getTipRectangle(MouseEvent event, Dimension tipContentSize) {
    Rectangle nodeBounds = new Rectangle(event.getPoint());
    TreePath pathForLocation = getPathForLocation(event.getX(), event.getY());
    if(pathForLocation != null) {
      nodeBounds = getPathBounds(pathForLocation);
    }

    Rectangle contentRect = getVisibleRect();
    int x, y;

    int vgap = nodeBounds.height;
    int height;
    int width = Math.min(tipContentSize.width, contentRect.width);
    if(event.getY() > contentRect.y + contentRect.height / 2) {
      y = Math.max(contentRect.y, nodeBounds.y - tipContentSize.height - vgap);
      height = Math.min(tipContentSize.height, nodeBounds.y - contentRect.y - vgap);
    } else {
      y = nodeBounds.y + nodeBounds.height + vgap;
      height = Math.min(tipContentSize.height, contentRect.height - y);
    }

    Dimension tipSize = new Dimension(width, height);

    x = event.getX() - width / 2;
    if(x < contentRect.x) x = contentRect.x;
    if(x + width > contentRect.x + contentRect.width) x = contentRect.x + contentRect.width - width;

    return new Rectangle(new Point(x, y), tipSize);
  }

  private String prepareToolTipText(String text) {
    int tabSize = CodeStyleSettingsManager.getSettings(myProject).getTabSize(StdFileTypes.JAVA);
    if (tabSize < 0) tabSize = 0;
    StringBuffer buf = new StringBuffer();
    boolean special = false;
    for(int idx = 0; idx < text.length(); idx++) {
      char c = text.charAt(idx);
      if(special) {
        if (c == 't') { // convert tabs to spaces
          for (int i = 0; i < tabSize; i++) {
            buf.append(' ');
          }
        }
        else if (c == 'r') { // remove occurances of '\r'
        }
        else if (c == 'n') {
          buf.append('\n');
        }
        else {
          buf.append('\\');
          buf.append(c);
        }
        special = false;
      } else {
        if(c == '\\') {
          special = true;
        } else {
          buf.append(c);
        }
      }
    }

    return buf.toString();
  }

  public void dispose() {
    myTipManager.dispose();
  }

}