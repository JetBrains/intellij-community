/*
 * Copyright 2002-2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import icons.XsltDebuggerIcons;
import org.intellij.plugins.xsltDebugger.rt.engine.OutputEventQueue;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 09.06.2007
 */
class GeneratedStructureRenderer extends ColoredTreeCellRenderer {

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    final Object o = node.getUserObject();

    setToolTipText(null);

    if (o == null || "ROOT".equals(o)) {
      // invisible
    } else if (o instanceof String) {
      // "..." node
      append((String)o, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
      setToolTipText("Element is not finished yet");
    } else if (o instanceof OutputEventQueue.NodeEvent) {
      final OutputEventQueue.NodeEvent event = (OutputEventQueue.NodeEvent)o;
      final OutputEventQueue.NodeEvent.QName qname = event.getQName();
      switch (event.getType()) {
        case OutputEventQueue.START_ELEMENT:
          setIcon(PlatformIcons.XML_TAG_ICON);
          append(qname.getQName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          if (qname.myURI != null && qname.myURI.length() > 0) {
            append(" {", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append(qname.myURI, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("}", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          break;
        case OutputEventQueue.ATTRIBUTE:
          setIcon(PlatformIcons.ANNOTATION_TYPE_ICON);
          append(qname.getQName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          if (qname.myURI != null && qname.myURI.length() > 0) {
            append(" {" + qname.myURI + "}", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          append(" = \"", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          append(event.getValue(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          append("\"", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          break;
        case OutputEventQueue.CHARACTERS:
          append("#text ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          append(clipValue(event.getValue()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          break;
        case OutputEventQueue.COMMENT:
          setIcon(XsltDebuggerIcons.XmlComment);
          append("#comment ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          append(event.getValue(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          break;
        case OutputEventQueue.PI:
          append("#processing-instruction ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          append(qname.myLocalName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          append(" " + event.getValue(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          break;
        case OutputEventQueue.TRACE_POINT:
          setIcon(AllIcons.Debugger.Db_set_breakpoint);
          append("Tracepoint at line " + event.getLineNumber(), SimpleTextAttributes.GRAY_ATTRIBUTES);
          if (event.getValue() != null) {
            append(" " + event.getValue(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          break;
      }

      if (node instanceof GeneratedStructureModel.StructureNode) {
        if (((GeneratedStructureModel.StructureNode)node).isNew()) {
          append(" *", SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        }
      }
    }
  }

  static String clipValue(String stringValue) {
    return stringValue.length() < 80 ? stringValue : stringValue.substring(0, 80) + "...";
  }
}
