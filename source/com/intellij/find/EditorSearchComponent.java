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
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorSearchComponent extends JPanel implements DataProvider {
  private final JLabel myMatchInfoLabel;
  private final Project myProject;
  private final Editor myEditor;
  private final JTextField mySearchField;
  private final Color myDefaultBackground;

  private final Color GRADIENT_C1;
  private final Color GRADIENT_C2;
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);
  public static final Color COMPLETION_BACKGROUND_COLOR = new Color(235, 244, 254);
  private final JComponent myToolbarComponent;

  @Nullable
  public Object getData(@NonNls final String dataId) {
    return null;
  }

  public EditorSearchComponent(final Editor editor, final Project project) {
    super(new BorderLayout(0, 0));

    GRADIENT_C1 = getBackground();
    GRADIENT_C2 = new Color(GRADIENT_C1.getRed() - 0x18, GRADIENT_C1.getGreen() - 0x18, GRADIENT_C1.getBlue() - 0x18);
    
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
    group.add(new ShowHistoryAction());
    group.add(new PrevOccurenceAction());
    group.add(new NextOccurenceAction());

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("SearchBar", group, true);
    tb.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbarComponent = tb.getComponent();
    myToolbarComponent.setBorder(null);
    myToolbarComponent.setOpaque(false);
    leadPanel.add(myToolbarComponent);

    final JCheckBox cbMatchCase = new NonFocusableCheckBox("Case sensitive");
    final JCheckBox cbWholeWords = new NonFocusableCheckBox("Match whole words only");

    leadPanel.add(cbMatchCase);
    leadPanel.add(cbWholeWords);

    cbMatchCase.setSelected(isCaseSensitive());
    cbWholeWords.setSelected(isWholeWords());

    cbMatchCase.setMnemonic('C');
    cbWholeWords.setMnemonic('M');

    setSmallerFontAndOpaque(cbWholeWords);
    setSmallerFontAndOpaque(cbMatchCase);

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
    setSmallerFontAndOpaque(myMatchInfoLabel);

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
          addCurrentTextToRecents();
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

    new VariantsCompletionAction(); // It registers a shortcut set automatically on construction
  }

  private void searchBackward() {
    if (mySearchField.getText().length() > 0) {
      final SelectionModel model = myEditor.getSelectionModel();
      if (model.hasSelection()) {
        if (Comparing.equal(mySearchField.getText(), model.getSelectedText(), isCaseSensitive()) && myEditor.getCaretModel().getOffset() == model.getSelectionEnd()) {
          myEditor.getCaretModel().moveToOffset(model.getSelectionStart());
        }
      }

      FindUtil.searchBack(myProject, myEditor);
      addCurrentTextToRecents();
    }
  }

  private void searchForward() {
    if (mySearchField.getText().length() > 0) {
      final SelectionModel model = myEditor.getSelectionModel();
      if (model.hasSelection()) {
        if (Comparing.equal(mySearchField.getText(), model.getSelectedText(), isCaseSensitive()) && myEditor.getCaretModel().getOffset() == model.getSelectionStart()) {
          myEditor.getCaretModel().moveToOffset(model.getSelectionEnd());
        }
      }

      FindUtil.searchAgain(myProject, myEditor);
      addCurrentTextToRecents();
    }
  }

  private void addCurrentTextToRecents() {
    final String text = mySearchField.getText();
    if (text.length() > 0) {
      FindSettings.getInstance().addStringToFind(text);
    }
  }

  private static void setSmallerFontAndOpaque(final JComponent component) {
    setSmallerFont(component);
    component.setOpaque(false);
  }

  private static void setSmallerFont(final JComponent component) {
    if (SystemInfo.isMac) {
      Font f = component.getFont();
      component.setFont(f.deriveFont(f.getStyle(), f.getSize() - 2));
    }
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
    addCurrentTextToRecents();
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
  }

  private void setNotFoundBackground() {
    mySearchField.setBackground(LightColors.RED);
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

  private class ShowHistoryAction extends AnAction {
    private ShowHistoryAction() {
      getTemplatePresentation().setIcon(IconLoader.findIcon("/actions/search.png"));
      getTemplatePresentation().setDescription("Search history");
      getTemplatePresentation().setText("Search History");

      ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
      shortcuts.add(new KeyboardShortcut(
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK), null));
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction("IncrementalSearch").getShortcutSet().getShortcuts()));
      shortcuts.addAll(Arrays.asList(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet().getShortcuts()));

      registerCustomShortcutSet(
        new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
        mySearchField);
    }

    public void actionPerformed(final AnActionEvent e) {
      showCompletionPopup(e, new JList(ArrayUtil.reverseArray(FindSettings.getInstance().getRecentFindStrings())), "Recent Searches");
    }
  }

  private class VariantsCompletionAction extends AnAction {
    private VariantsCompletionAction() {
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION).getShortcutSet(), mySearchField);
    }

    public void actionPerformed(final AnActionEvent e) {
      final String prefix = getPrefix();
      if (prefix.length() == 0) return;

      final String[] array = calcWords(prefix);
      if (array.length == 0) {
        return;
      }

      final JList list = new JList(array);
      list.setBackground(COMPLETION_BACKGROUND_COLOR);
      list.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

      showCompletionPopup(e, list, null);
    }

    private String getPrefix() {
      return mySearchField.getText().substring(0, mySearchField.getCaret().getDot());
    }

    private String[] calcWords(final String prefix) {
      final String regexp = NameUtil.buildRegexp(prefix, 0, true, true);
      final Pattern pattern = Pattern.compile(regexp);
      final Matcher matcher = pattern.matcher("");
      final Set<String> words = new HashSet<String>();
      CharSequence chars = myEditor.getDocument().getCharsSequence();

      IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
        public void run(final CharSequence chars, final int start, final int end, char[] charArray) {
          final CharSequence word = chars.subSequence(start, end);
          matcher.reset(word);
          if (matcher.matches()) {
            words.add(word.toString());
          }
        }
      }, chars, 0, chars.length());


      ArrayList<String> sortedWords = new ArrayList<String>(words);
      Collections.sort(sortedWords);

      return sortedWords.toArray(new String[sortedWords.size()]);
    }
  }

  private void showCompletionPopup(final AnActionEvent e, final JList list, String title) {

    final Runnable callback = new Runnable() {
      public void run() {
        String selectedValue = (String)list.getSelectedValue();
        if (selectedValue != null) {
          mySearchField.setText(selectedValue);
        }
      }
    };

    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }

    final JBPopup popup = builder.setMovable(false).setResizable(false)
      .setRequestFocus(true).setItemChoosenCallback(callback).createPopup();

    final InputEvent event = e.getInputEvent();
    if (event instanceof MouseEvent) {
      popup.showUnderneathOf(myToolbarComponent);
    }
    else {
      popup.showUnderneathOf(mySearchField);
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