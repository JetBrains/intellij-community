package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListPopup;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

public class PopupActionChooser {
  private final String myTitle;

  public static interface Callback {
    void execute(Object actionObject);
    boolean isApplicable(Object actionObject);
  }

  public PopupActionChooser(String title) {
    myTitle = title;
  }

  public void invoke(final Project project, final Editor editor, final Surrounder[] surrounders, final PsiElement[] elements){
    final ArrayList<Surrounder> applicable = new ArrayList<Surrounder>();
    final ArrayList<String> actionNames = new ArrayList<String>();
    for (Surrounder surrounder : surrounders) {
      if (surrounder.isApplicable(elements)) {
        actionNames.add(surrounder.getTemplateDescription());
        applicable.add(surrounder);
      }
    }

    if (applicable.size() == 0) return;

    final JList list = new JList(actionNames.toArray());

    list.setCellRenderer(new MyListCellRenderer());

    final Runnable listener = new Runnable(){
      public void run() {
        int index = list.getSelectedIndex();
        if (index >= 0){
          final Surrounder surrounder = applicable.get(index);
          CommandProcessor.getInstance().executeCommand(
              project, new Runnable(){
              public void run(){
                final Runnable action = new Runnable(){
                  public void run(){
                    SurroundWithHandler.doSurround(project, editor, surrounder, elements);
                  }
                };
                ApplicationManager.getApplication().runWriteAction(action);
              }
            },
            null,
            null
          );
        }
      }
    };

    final ListPopup listPopup = new ListPopup(myTitle, list, listener, project);

    list.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          char c = e.getKeyChar();
          int index = -1;
          if ('0' <= c && c <= '9'){
            index = c == '0' ? 9 : c - '1';
          }
          else if ('A' <= c && c <= 'Z'){
            index = c - 'A' + 10;
          }
          else if ('a' <= c && c <= 'z'){
            index = c - 'a' + 10;
          }
          if (index >= 0 && index < list.getModel().getSize()) {
            list.setSelectedIndex(index);
            listener.run();
            listPopup.closePopup(false);
          }
        }
      }
    );

    LogicalPosition caretLogicalPosition = editor.getCaretModel().getLogicalPosition();
    Point caretLocation = editor.logicalPositionToXY(new LogicalPosition(caretLogicalPosition.line + 1, caretLogicalPosition.column));
    Point location = editor.getContentComponent().getLocationOnScreen();
    listPopup.show(caretLocation.x + location.x, caretLocation.y + location.y); //TODO : check screen bounds!
  }

  private final class MyListCellRenderer extends ColoredListCellRenderer{
    private final SimpleTextAttributes myAttributes;

    public MyListCellRenderer(){
      myAttributes=SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    }

    protected void customizeCellRenderer(
      JList list,
      Object value,
      int index,
      boolean selected,
      boolean hasFocus
    ){
      String name = (String)value;
      if (index < 9){
        name = (index + 1) + ". " + name;
      }
      else if (index == 9){
        name = 0 + ". " + name;
      }
      else {
        name = (char)('A' + index - 10) + ". " + name;
      }
      append(name,myAttributes);
    }
  }
}