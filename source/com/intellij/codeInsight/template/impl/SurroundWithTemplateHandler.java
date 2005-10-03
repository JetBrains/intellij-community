package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ListPopup;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.SpeedSearchBase;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author mike
 */
public class SurroundWithTemplateHandler implements CodeInsightActionHandler {
  public SurroundWithTemplateHandler() {
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!file.isWritable()) return;
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
      if (!editor.getSelectionModel().hasSelection()) return;
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    int offset = editor.getCaretModel().getOffset();
    int contextType = TemplateManager.getInstance(project).getContextType(file, offset);
    TemplateImpl[] templates = TemplateSettings.getInstance().getTemplates();
    ArrayList<TemplateImpl> array = new ArrayList<TemplateImpl>();
    for (TemplateImpl template : templates) {
      if (template.isDeactivated()) continue;
      if (template.getTemplateContext().isInContext(contextType) && template.isSelectionTemplate()) {
        array.add(template);
      }
    }
    if (array.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, CodeInsightBundle.message("templates.no.defined"));
      return;
    }
    Collections.sort(array, new Comparator<TemplateImpl>() {
      public int compare(TemplateImpl o1, TemplateImpl o2) {
        return o1.getKey().compareTo(o2.getKey());
      }
    });
    final TemplateImpl[] listData = array.toArray(new TemplateImpl[array.size()]);
    final JList list = new JList(listData);
    list.setCellRenderer(new MyListCellRenderer(listData));
    ListPopup listPopup = new ListPopup(
      CodeInsightBundle.message("templates.select.template.chooser.title"),
      list,
      new Runnable() {
        public void run() {
          String selectionString = editor.getSelectionModel().getSelectedText();
          TemplateImpl template = (TemplateImpl)list.getSelectedValue();
          if (template == null) return;

          if (selectionString != null) {
            if (template.isToReformat()) selectionString = selectionString.trim();
          }
          TemplateManager.getInstance(project).startTemplate(editor, selectionString, template);
        }
      },
      project
    );
    new MySpeedSearchBase(listPopup, list, listData);
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Point caretLocation = editor.logicalPositionToXY(new LogicalPosition(pos.line + 1, pos.column));
    int y = caretLocation.y;
    int x = caretLocation.x;
    Point location = editor.getContentComponent().getLocationOnScreen();
    x += location.x;
    y += location.y;
    listPopup.show(x, y);
  }

  public boolean startInWriteAction() {
    return true;
  }

  private class MyListCellRenderer extends JPanel implements ListCellRenderer {
    private JLabel myAbbreviation = new JLabel();
    private JLabel myDescription = new JLabel();

    public MyListCellRenderer(TemplateImpl[] templates) {
      setLayout(new BorderLayout());
      add(myAbbreviation, BorderLayout.WEST);
      add(myDescription, BorderLayout.CENTER);
      myAbbreviation.setAlignmentX(JComponent.LEFT_ALIGNMENT);
      myDescription.setAlignmentX(JComponent.LEFT_ALIGNMENT);
      myAbbreviation.setHorizontalAlignment(JCheckBox.LEFT);
      myDescription.setHorizontalAlignment(JCheckBox.LEFT);
      myAbbreviation.setOpaque(true);
      myDescription.setOpaque(true);
      int width = 0;
      int height = 0;
      for (TemplateImpl template : templates) {
        myAbbreviation.setText(getAbbreviation(template));
        final Dimension preferredSize = myAbbreviation.getPreferredSize();
        width = Math.max(width, preferredSize.width);
        height = Math.max(height, preferredSize.height);
      }
      myAbbreviation.setPreferredSize(new Dimension(width, height));
      myAbbreviation.setMinimumSize(new Dimension(width, height));
      myAbbreviation.setMaximumSize(new Dimension(width, height));
    }

    public Component getListCellRendererComponent(
      JList list,
      Object value,
      int index,
      boolean isSelected,
      boolean cellHasFocus) {
      TemplateImpl template = (TemplateImpl)value;
      myAbbreviation.setText(getAbbreviation(template));
      myDescription.setText(" " + template.getDescription() + " ");
      if (isSelected) {
        myAbbreviation.setBackground(list.getSelectionBackground());
        myDescription.setBackground(list.getSelectionBackground());
        setBackground(list.getSelectionBackground());
        myAbbreviation.setForeground(list.getSelectionForeground());
        myDescription.setForeground(list.getSelectionForeground());
      }
      else {
        myAbbreviation.setBackground(list.getBackground());
        myDescription.setBackground(list.getBackground());
        myAbbreviation.setForeground(list.getForeground());
        myDescription.setForeground(list.getForeground());
        setBackground(list.getBackground());
      }
      if (cellHasFocus) {
        setBorder(BorderFactory.createLineBorder(Color.black));
      }
      else {
        setBorder(BorderFactory.createLineBorder(list.getBackground()));
      }
      return this;
    }

    private String getAbbreviation(TemplateImpl template) {
      return " " + template.getKey() + " ";
    }
  }

  private class MySpeedSearchBase extends SpeedSearchBase<JList> {
    private ListPopup myListPopup;
    private final JList myList;
    private final TemplateImpl[] myListData;

    public MySpeedSearchBase(ListPopup listPopup, JList list, TemplateImpl[] listData) {
      super(list);
      myListPopup = listPopup;
      myList = list;
      myListData = listData;
    }

    protected Object[] getAllElements() {
      return myListData;
    }

    protected String getElementText(Object element) {
      return ((TemplateImpl)element).getKey();
    }

    protected int getSelectedIndex() {
      return myList.getSelectedIndex();
    }

    protected void selectElement(Object element, String selectedText) {
      ListScrollingUtil.selectItem(myList, element);
      int matches = 0;
      TemplateImpl result = null;
      for (TemplateImpl template : myListData) {
        if (template.getKey().toLowerCase().startsWith(selectedText.toLowerCase())) matches++;
        if (template.getKey().equalsIgnoreCase(selectedText)) result = template;
      }
      if (matches == 1 && result != null) {
        myListPopup.closePopup(true);
      }
    }
  }
}
