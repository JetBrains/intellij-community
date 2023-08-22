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

package com.intellij.util.xml.ui;

import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.ui.JBColor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementsProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ErrorableTableCellRenderer<T extends DomElement> extends DefaultTableCellRenderer {
  private final TableCellRenderer myRenderer;
  private final DomElement myRowDomElement;
  private final T myCellValueDomElement;
  private final DomElement myRoot;

  public ErrorableTableCellRenderer(@Nullable final T cellValueDomElement, final TableCellRenderer renderer, @NotNull final DomElement rowDomElement) {
    myCellValueDomElement = cellValueDomElement;
    myRenderer = renderer;
    myRowDomElement = rowDomElement;

    myRoot = DomUtil.getRoot(myRowDomElement);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component = myRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    if (!myRoot.isValid()) {
      return component;
    }

    final DomElementAnnotationsManager annotationsManager = DomElementAnnotationsManager.getInstance(myRowDomElement.getManager().getProject());
    final DomElementsProblemsHolder holder = annotationsManager.getCachedProblemHolder(myRoot);
    final List<DomElementProblemDescriptor> errorProblems = holder.getProblems(myCellValueDomElement);
    final List<DomElementProblemDescriptor> warningProblems =
      new ArrayList<>(holder.getProblems(myCellValueDomElement, true, HighlightSeverity.WARNING));
    warningProblems.removeAll(errorProblems);

    final boolean hasErrors = errorProblems.size() > 0;
    if (hasErrors) {
      component.setForeground(JBColor.RED);
      if (component instanceof JComponent) {
        ((JComponent)component).setToolTipText(TooltipUtils.getTooltipText(errorProblems));
      }
    }
    else {
      component.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      if (component instanceof JComponent) {
        ((JComponent)component).setToolTipText(null);
      }
    }

    // highlight empty cell with errors
    if (hasErrors && (value == null || value.toString().trim().length() == 0)) {
      component.setBackground(BaseControl.ERROR_BACKGROUND);
    }
    else if (warningProblems.size() > 0) {
      component.setBackground(BaseControl.WARNING_BACKGROUND);
      if(isSelected) component.setForeground(JBColor.foreground());
    }

    final List<DomElementProblemDescriptor> errorDescriptors =
      annotationsManager.getCachedProblemHolder(myRowDomElement).getProblems(myRowDomElement, true, true);

    if (table.getModel().getColumnCount() - 1 == column) {
      if (errorDescriptors.size() > 0) {
        final JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(component, BorderLayout.CENTER);

        wrapper.setBackground(component.getBackground());

        final JLabel errorLabel = new JLabel(getErrorIcon());

        wrapper.setToolTipText(TooltipUtils.getTooltipText(errorDescriptors));

        wrapper.add(errorLabel, BorderLayout.EAST);

        if (component instanceof JComponent jComponent) {
          wrapper.setBorder(jComponent.getBorder());
          jComponent.setBorder(BorderFactory.createEmptyBorder());
          jComponent.setToolTipText(TooltipUtils.getTooltipText(errorDescriptors));
        }

        return wrapper;
      } else {
        if (component instanceof JComponent) {
          ((JComponent)component).setToolTipText(null);
        }
      }
    }

    return component;
  }

  private static Icon getErrorIcon() {
    return AllIcons.General.ExclMark;
  }
}
