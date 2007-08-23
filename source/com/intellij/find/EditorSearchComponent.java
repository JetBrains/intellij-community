/*
 * @author max
 */
package com.intellij.find;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.components.panels.NonOpaquePanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class EditorSearchComponent extends JPanel implements DataProvider {
  private final JLabel myMatchInfoLabel;
  private final Project myProject;
  private final Editor myEditor;
  private final JTextField mySearchField;
  private final Color myDefaultBackground;

  private static final Color GRADIENT_C1 = new Color(0xe8, 0xe8, 0xe8);
  private static final Color GRADIENT_C2 = new Color(0xD0, 0xD0, 0xD0);
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);

  @Nullable
  public Object getData(@NonNls final String dataId) {
    return null;
  }

  public EditorSearchComponent(final Editor editor, final Project project) {
    super(new BorderLayout(0, 0));

    myProject = project;
    myEditor = editor;

    JPanel leadPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    add(leadPanel, BorderLayout.WEST);

    mySearchField = new JTextField();
    mySearchField.putClientProperty("AuxEditorComponent", Boolean.TRUE);
    leadPanel.add(mySearchField);

    myDefaultBackground = mySearchField.getBackground();
    mySearchField.setColumns(25);

    setSmallerFont(mySearchField);

    DefaultActionGroup group = new DefaultActionGroup("search bar", false);
    group.add(new PrevOccurenceAction());

    group.add(new NextOccurenceAction());

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("SearchBar", group, true);
    tb.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    final JComponent prevnextToolbar = tb.getComponent();
    prevnextToolbar.setBorder(null);
    prevnextToolbar.setOpaque(false);
    leadPanel.add(prevnextToolbar);

    final JCheckBox cbMatchCase = new NonFocusableCheckBox("Case sensitive");
    final JCheckBox cbWholeWords = new NonFocusableCheckBox("Match whole words only");

    leadPanel.add(cbMatchCase);
    leadPanel.add(cbWholeWords);

    cbMatchCase.setSelected(isCaseSensitive());
    cbWholeWords.setSelected(isWholeWords());

    cbMatchCase.setMnemonic('C');
    cbWholeWords.setMnemonic('M');

    setSmallerFont(cbWholeWords);
    setSmallerFont(cbMatchCase);

    cbMatchCase.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = cbMatchCase.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setCaseSensitive(b);
        FindSettings.getInstance().setLocalCaseSensitive(b);
        updateResults();
      }
    });

    cbWholeWords.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean b = cbWholeWords.isSelected();
        FindManager.getInstance(myProject).getFindInFileModel().setWholeWordsOnly(b);
        FindSettings.getInstance().setLocalWholeWordsOnly(b);
        updateResults();
      }
    });

    JPanel tailPanel = new NonOpaquePanel(new BorderLayout(5, 0));
    add(tailPanel, BorderLayout.EAST);

    myMatchInfoLabel = new JLabel();
    setSmallerFont(myMatchInfoLabel);

    JLabel closeLabel = new JLabel(" ", IconLoader.getIcon("/actions/cross.png"), JLabel.RIGHT);
    closeLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        close();
      }
    });

    closeLabel.setToolTipText("Close search bar (Escape)");
    
    tailPanel.add(closeLabel, BorderLayout.EAST);
    tailPanel.add(myMatchInfoLabel, BorderLayout.WEST);

    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateResults();
      }
    });

    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        close();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if ("".equals(mySearchField.getText())) {
          close();
        }
        else {
          myEditor.getContentComponent().requestFocus();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        searchForward();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);

    mySearchField.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        searchBackward();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), JComponent.WHEN_FOCUSED);

    final String initialText = myEditor.getSelectionModel().getSelectedText();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        mySearchField.setText(initialText != null && initialText.indexOf('\n') < 0 ? initialText : "");
      }
    });
  }

  private void searchBackward() {
    if (mySearchField.getText().length() > 0) {
      final SelectionModel model = myEditor.getSelectionModel();
      if (model.hasSelection()) {
        if (mySearchField.getText().equals(model.getSelectedText()) && myEditor.getCaretModel().getOffset() == model.getSelectionEnd()) {
          myEditor.getCaretModel().moveToOffset(model.getSelectionStart());
        }
      }

      FindUtil.searchBack(myProject, myEditor);
    }
  }

  private void searchForward() {
    if (mySearchField.getText().length() > 0) {
      final SelectionModel model = myEditor.getSelectionModel();
      if (model.hasSelection()) {
        if (mySearchField.getText().equals(model.getSelectedText()) && myEditor.getCaretModel().getOffset() == model.getSelectionStart()) {
          myEditor.getCaretModel().moveToOffset(model.getSelectionEnd());
        }
      }

      FindUtil.searchAgain(myProject, myEditor);
    }
  }

  private static void setSmallerFont(final JComponent component) {
    if (SystemInfo.isMac) {
      Font f = component.getFont();
      component.setFont(f.deriveFont(f.getStyle(), f.getSize() - 2));
    }
    component.setOpaque(false);
  }

  public void requestFocus() {
    mySearchField.requestFocus();
  }

  private void close() {
    removeCurrentHighlights();
    if (myEditor.getSelectionModel().hasSelection()) {
      myEditor.getCaretModel().moveToOffset(myEditor.getSelectionModel().getSelectionStart());
      myEditor.getSelectionModel().removeSelection();
    }
    myEditor.setHeaderComponent(null);
  }

  private void updateResults() {
    removeCurrentHighlights();
    myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.PLAIN));
    String text = mySearchField.getText();
    if (text.length() == 0) {
      setRegularBackground();
      myMatchInfoLabel.setText("");
    }
    else {
      FindManager findManager = FindManager.getInstance(myProject);
      FindModel model = new FindModel();
      model.setCaseSensitive(isCaseSensitive());
      model.setWholeWordsOnly(isWholeWords());
      model.setFromCursor(false);
      model.setStringToFind(text);
      model.setSearchHighlighters(true);
      int offset = 0;
      ArrayList<FindResult> results = new ArrayList<FindResult>();
      while (true) {
        FindResult result = findManager.findString(myEditor.getDocument().getCharsSequence(), offset, model);
        if (!result.isStringFound()) break;
        offset = result.getEndOffset();
        results.add(result);

        if (results.size() > 100) break;
      }

      int currentOffset = myEditor.getCaretModel().getOffset();
      if (myEditor.getSelectionModel().hasSelection()) {
        currentOffset = Math.min(currentOffset, myEditor.getSelectionModel().getSelectionStart());
      }

      if (!findAndSelectFirstUsage(findManager, model, currentOffset)) {
        findAndSelectFirstUsage(findManager, model, 0);
      }

      final int count = results.size();
      if (count <= 100) {
        highlightResults(text, results);

        if (count > 0) {
          setRegularBackground();
          if (count > 1) {
            myMatchInfoLabel.setText("" + count + " matches");
          }
          else {
            myMatchInfoLabel.setText("1 match");
          }
        }
        else {
          setNotFoundBackground();
          myMatchInfoLabel.setText("No matches");
        }
      }
      else {
        setRegularBackground();
        myMatchInfoLabel.setText("More than 100 matches");
        myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.BOLD));
      }

      findManager.setFindWasPerformed();
      findManager.setFindNextModel(model);
    }
  }

  private void setRegularBackground() {
    mySearchField.setBackground(myDefaultBackground);
    mySearchField.setOpaque(false);
  }

  private void setNotFoundBackground() {
    mySearchField.setBackground(LightColors.RED);
    mySearchField.setOpaque(true);
  }

  private boolean isWholeWords() {
    return FindManager.getInstance(myProject).getFindInFileModel().isWholeWordsOnly();
  }

  private boolean isCaseSensitive() {
    return FindManager.getInstance(myProject).getFindInFileModel().isCaseSensitive();
  }

  private void removeCurrentHighlights() {
    ((HighlightManagerImpl)HighlightManager.getInstance(myProject))
          .hideHighlights(myEditor, HighlightManager.HIDE_BY_ESCAPE | HighlightManager.HIDE_BY_ANY_KEY);
  }

  private boolean findAndSelectFirstUsage(final FindManager findManager, final FindModel model, final int offset) {
    final FindResult firstResult;
    firstResult = findManager.findString(myEditor.getDocument().getCharsSequence(), offset, model);
    if (firstResult.isStringFound()) {
      myEditor.getSelectionModel().setSelection(firstResult.getStartOffset(), firstResult.getEndOffset());
      myEditor.getCaretModel().moveToOffset(firstResult.getStartOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return true;
    }

    return false;
  }

  private void highlightResults(final String text, final ArrayList<FindResult> results) {
    HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);

    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    for (FindResult result : results) {
      highlightManager.addRangeHighlight(myEditor, result.getStartOffset(), result.getEndOffset(), attributes, false, highlighters);
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setErrorStripeTooltip(text);
    }
  }

  private class PrevOccurenceAction extends AnAction {
    public PrevOccurenceAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE));
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS));
    }

    public void actionPerformed(final AnActionEvent e) {
      searchBackward();
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(mySearchField.getText().length() > 0);
    }
  }

  private class NextOccurenceAction extends AnAction {
    public NextOccurenceAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT));
    }

    public void actionPerformed(final AnActionEvent e) {
      searchForward();
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(mySearchField.getText().length() > 0);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2d = (Graphics2D) g;

    g.setColor(BORDER_COLOR);
    g.drawLine(0, 0, getWidth(), 0);
    g.drawLine(0, 0, 0, getHeight());

//    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
    g2d.setPaint(new GradientPaint(0, 0, GRADIENT_C1, 0, getHeight(), GRADIENT_C2));

    g2d.fillRect(1, 1, getWidth(), getHeight() - 1);
  }  
}