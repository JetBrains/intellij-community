package com.intellij.debugger.ui;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 2:39:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class DebuggerStatementEditor extends DebuggerEditorImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerStatementEditor");

  private final EditorTextField myEditor;

  private int    myRecentIdx;

  public DebuggerStatementEditor(Project project, PsiElement context, String recentsId) {
    super(project, context, recentsId);
    myRecentIdx = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId()).size();
    myEditor = new EditorTextField("", project, StdFileTypes.JAVA) {
      protected EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.setOneLineMode(false);
        return editor;
      }
    };
    setLayout(new BorderLayout());
    add(myEditor, BorderLayout.CENTER);

    DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
    actionGroup.add(new ItemAction(IdeActions.ACTION_PREVIOUS_OCCURENCE, this){
      public void actionPerformed(AnActionEvent e) {
        LOG.assertTrue(myRecentIdx > 0);
        myRecentIdx --;
        updateTextFromRecents();
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myRecentIdx > 0);
      }
    });
    actionGroup.add(new ItemAction(IdeActions.ACTION_NEXT_OCCURENCE, this){
      public void actionPerformed(AnActionEvent e) {
        if(LOG.isDebugEnabled()) {
          LinkedList<TextWithImportsImpl> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
          LOG.assertTrue(myRecentIdx < recents.size());
        }
        myRecentIdx ++;
        updateTextFromRecents();
      }

      public void update(AnActionEvent e) {
        LinkedList<TextWithImportsImpl> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
        e.getPresentation().setEnabled(myRecentIdx < recents.size());
      }
    });

    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.COMBO_PAGER, actionGroup, false).getComponent(),
        BorderLayout.EAST);

    setText(TextWithImportsImpl.EMPTY);
  }

  private void updateTextFromRecents() {
    LinkedList<TextWithImportsImpl> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
    LOG.assertTrue(myRecentIdx <= recents.size());
    setText(myRecentIdx < recents.size() ? recents.get(myRecentIdx) : TextWithImportsImpl.EMPTY);
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getEditor().getContentComponent();
  }

  public TextWithImportsImpl getText() {
    return createItem(myEditor.getDocument(), getProject());
  }

  public void setText(TextWithImports text) {
    myEditor.setDocument(createDocument((TextWithImportsImpl)text));
  }

  public TextWithImportsImpl createText(String text, String importsString) {
    return new TextWithImportsImpl(TextWithImportsImpl.CODE_BLOCK_FACTORY, text, importsString);
  }

  private static abstract class ItemAction extends AnAction {
    public ItemAction(String sourceActionName, JComponent component) {
      copyFrom(ActionManager.getInstance().getAction(sourceActionName));
      registerCustomShortcutSet(getShortcutSet(), component);
    }
  }

}
