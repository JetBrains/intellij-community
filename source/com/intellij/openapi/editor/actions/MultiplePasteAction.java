package com.intellij.openapi.editor.actions;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author max
 */
public class MultiplePasteAction extends AnAction {
  private static final Icon textIcon = IconLoader.getIcon("/fileTypes/text.png");

  public MultiplePasteAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Component focusedComponent = (Component)dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT);
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);

    if (!(focusedComponent instanceof JComponent)) return;

    final Chooser chooser = new Chooser(project);

    if (chooser.myAllContents.length > 0) {
      chooser.show();
    }
    else {
      chooser.close(Chooser.CANCEL_EXIT_CODE);
    }

    if (chooser.isOK()) {
      final int selectedIndex = chooser.getSelectedIndex();
      ((CopyPasteManagerEx)CopyPasteManager.getInstance()).moveContentTopStackTop(chooser.myAllContents[selectedIndex]);

      if (editor != null) {
        if (!editor.getDocument().isWritable()) {
          editor.getDocument().fireReadOnlyModificationAttempt();
          return;
        }

        final AnAction pasteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE);
        AnActionEvent newEvent = new AnActionEvent(e.getInputEvent(),
                                                   DataManager.getInstance().getDataContext(focusedComponent),
                                                   e.getPlace(), e.getPresentation(),
                                                   ActionManager.getInstance(),
                                                   e.getModifiers());
        pasteAction.actionPerformed(newEvent);
      }
      else {
        final Action pasteAction = ((JComponent)focusedComponent).getActionMap().get(DefaultEditorKit.pasteAction);
        if (pasteAction != null) {
          pasteAction.actionPerformed(new ActionEvent(focusedComponent, ActionEvent.ACTION_PERFORMED, ""));
        }
      }
    }
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e.getDataContext()));
  }

  private boolean isEnabled(DataContext dataContext) {
    Object component = dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT);
    if (!(component instanceof JComponent)) return false;
    if (dataContext.getData(DataConstants.EDITOR) != null) return true;
    Action pasteAction = ((JComponent)component).getActionMap().get(DefaultEditorKit.pasteAction);
    return pasteAction != null;
  }

  private static class Chooser extends DialogWrapper {
    private JList myList;
    private Transferable[] myAllContents;
    private Editor myViewer;
    private Splitter mySplitter;
    private Project myProject;

    public Chooser(Project project) {
      super(project, true);
      myProject = project;

      setOKButtonText("OK");
      setTitle("Choose Content to Paste");

      init();
    }

    public JComponent getPreferredFocusedComponent() {
      return myList;
    }

    protected JComponent createCenterPanel() {
      myList = new JList();
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      rebuildListContent();

      myList.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.isConsumed() || e.getClickCount() != 2 || e.isPopupTrigger()) return;
          close(OK_EXIT_CODE);
        }
      });

      myList.setCellRenderer(new MyListCellRenderer());

      if (myAllContents.length > 0) {
        myList.setSelectedIndex(0);
      }

      myList.addKeyListener(new KeyAdapter() {
        public void keyReleased(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_DELETE) {
            int selectedIndex = getSelectedIndex();
            int size = myAllContents.length;
            ((CopyPasteManagerEx)CopyPasteManager.getInstance()).removeContent(myAllContents[selectedIndex]);
            rebuildListContent();
            if (size == 1) {
              close(CANCEL_EXIT_CODE);
              return;
            }
            myList.setSelectedIndex(Math.min(selectedIndex, myAllContents.length - 1));
          }
          else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            close(OK_EXIT_CODE);
          }
        }
      });

      mySplitter = new Splitter(true);
      mySplitter.setFirstComponent(new JScrollPane(myList));
      mySplitter.setSecondComponent(new JPanel());
      updateViewerForSelection();

      myList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateViewerForSelection();
        }
      });

      mySplitter.setPreferredSize(new Dimension(500, 500));

      return mySplitter;
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.openapi.editor.actions.MultiplePasteAction.Chooser";
    }

    private void updateViewerForSelection() {
      if (myAllContents.length == 0) return;

      try {
        Transferable content = myAllContents[getSelectedIndex()];
        String fullString = (String)content.getTransferData(DataFlavor.stringFlavor);
        fullString = StringUtil.convertLineSeparators(fullString);
        Document doc = EditorFactory.getInstance().createDocument(fullString);
        if (myViewer != null) {
          EditorFactory.getInstance().releaseEditor(myViewer);
        }

        myViewer = EditorFactory.getInstance().createViewer(doc, myProject);
        myViewer.getComponent().setPreferredSize(new Dimension(300, 500));
        myViewer.getSettings().setFoldingOutlineShown(false);
        myViewer.getSettings().setLineNumbersShown(false);
        myViewer.getSettings().setLineMarkerAreaShown(false);
        mySplitter.setSecondComponent(myViewer.getComponent());
        mySplitter.revalidate();
      }
      catch (UnsupportedFlavorException e1) {
      }
      catch (IOException e1) {
      }
    }

    protected void dispose() {
      super.dispose();
      if (myViewer != null) {
        EditorFactory.getInstance().releaseEditor(myViewer);
        myViewer = null;
      }
    }

    private void rebuildListContent() {
      Transferable[] allContents = CopyPasteManager.getInstance().getAllContents();
      ArrayList<Transferable> contents = new ArrayList<Transferable>();
      ArrayList shortened = new ArrayList();
      for (int i = 0; i < allContents.length; i++) {
        Transferable content = allContents[i];
        try {
          String fullString = (String)content.getTransferData(DataFlavor.stringFlavor);
          if (fullString != null) {
            fullString = StringUtil.convertLineSeparators(fullString, "\n");
            contents.add(content);
            int lastNewLineIdx = fullString.indexOf('\n');
            shortened.add(lastNewLineIdx == -1 ? fullString : fullString.substring(0, lastNewLineIdx) + " ...");
          }
        }
        catch (UnsupportedFlavorException e) {
        }
        catch (IOException e) {
        }
      }

      myAllContents = contents.toArray(new Transferable[contents.size()]);
      myList.setListData(shortened.toArray(new String[shortened.size()]));
    }

    private int getSelectedIndex() {
      if (myList.getSelectedIndex() == -1) return 0;
      return myList.getSelectedIndex();
    }

    private class MyListCellRenderer extends DefaultListCellRenderer {
      public Component getListCellRendererComponent(
        JList list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setIcon(textIcon);
        setText((String)value);
        return this;
      }
    }
  }
}
