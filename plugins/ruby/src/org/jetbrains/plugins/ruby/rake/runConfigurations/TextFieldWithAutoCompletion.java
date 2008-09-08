package org.jetbrains.plugins.ruby.rake.runConfigurations;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.codeInsight.hint.HintUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.lang.TextUtil;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roman Chernyatchik
 */
public class TextFieldWithAutoCompletion extends JTextField {
  //TODO refactor with something
  private static final Color COMPLETION_BACKGROUND_COLOR = new Color(235, 244, 254);

  private List<String> myVariants;
  private Project myProject;
  //private TextFieldWithAutoCompletion.CancelAction myCancelAction;
  //private Set<Action> myDisabledTextActions;
  //private JBPopup myCurrentPopup;



  public TextFieldWithAutoCompletion(@Nullable final List<String> variants, final Project project) {
    myProject = project;
    setVariants(variants);

    //putClientProperty("AuxEditorComponent", Boolean.TRUE);
    new VariantsCompletionAction();

    //final InputMap listMap = (InputMap)UIManager.getDefaults().get("List.focusInputMap");
    //final KeyStroke[] listKeys = listMap.keys();
    //myDisabledTextActions = new HashSet<Action>();
    //for (KeyStroke eachListStroke : listKeys) {
    //  final String listActionID = (String)listMap.get(eachListStroke);
    //  if ("selectNextRow".equals(listActionID) || "selectPreviousRow".equals(listActionID)) {
    //    final String textActionID = (String)getInputMap().get(eachListStroke);
    //    if (textActionID != null) {
    //      final Action textAction = getActionMap().get(textActionID);
    //      if (textAction != null) {
    //        myDisabledTextActions.add(textAction);
    //      }
    //    }
    //  }
    //}
    //addFocusListener(new FocusAdapter() {
    //  public void focusLost(final FocusEvent e) {
    //    closePopup();
    //  }
    //});
    //
    //
    //addKeyListener(new KeyAdapter() {
    //  public void keyPressed(final KeyEvent e) {
    //    processListSelection(e);
    //  }
    //});
    //

    //myCancelAction = new CancelAction();
  }

  private class VariantsCompletionAction extends AnAction {
    private VariantsCompletionAction() {
      final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), TextFieldWithAutoCompletion.this);
      }
    }

    public void actionPerformed(final AnActionEvent e) {
      final String[] array = calcWords(getPrefix());
      if (array.length == 0) {
        showNoSuggestionsPopup();
        return;
      }

      FeatureUsageTracker.getInstance().triggerFeatureUsed("find.completion");
      final JList list = new JList(array) {
        protected void paintComponent(final Graphics g) {
          UISettings.setupAntialiasing(g);
          super.paintComponent(g);
        }
      };
      list.setBackground(COMPLETION_BACKGROUND_COLOR);
      //list.setBackground(getBackground());

      showCompletionPopup(list, null);
    }

    private String getPrefix() {
      return getText().substring(0, getCaret().getDot());
    }

    private String[] calcWords(final String prefix) {
      final Set<String> words = new HashSet<String>();
      if (TextUtil.isEmpty(prefix)) {
        for (String variant : myVariants) {
          words.add(variant);
        }
      } else {
        final String regexp = NameUtil.buildRegexp(prefix, 0, true, true);
        final Pattern pattern = Pattern.compile(regexp);
        final Matcher matcher = pattern.matcher("");

        for (String variant : myVariants) {
          matcher.reset(variant);
          if (matcher.matches()) {
            words.add(variant);
          }
        }
      }

      final ArrayList<String> sortedWords = new ArrayList<String>(words);
      Collections.sort(sortedWords);

      return sortedWords.toArray(new String[sortedWords.size()]);
    }
  }

  private void showNoSuggestionsPopup() {
    final JLabel message = HintUtil.createErrorLabel(IdeBundle.message("file.chooser.completion.no.suggestions"));
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(message, message);
    builder.setRequestFocus(true).setResizable(false).setAlpha(0.1f).setFocusOwners(new Component[] {this});
    builder.createPopup().showUnderneathOf(this);
  }

  public void setVariants(@Nullable final List<String> variants) {
    if (variants == null) {
      myVariants = Collections.emptyList();
      return;
    }
    myVariants = variants;
  }

  //@SuppressWarnings("HardCodedStringLiteral")
  //private void processListSelection(final KeyEvent e) {
  //  if (togglePopup(e)) return;
  //
  //  if (!isPopupShowing()) return;
  //
  //  final InputMap map = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
  //  if (map != null) {
  //    final Object object = map.get(KeyStroke.getKeyStrokeForEvent(e));
  //    if (object instanceof Action) {
  //      final Action action = (Action)object;
  //      if (action.isEnabled()) {
  //        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "action"));
  //        e.consume();
  //        return;
  //      }
  //    }
  //  }
  //
  //  final Object action = getAction(e, myList);
  //
  //  if ("selectNextRow".equals(action)) {
  //    if (ensureSelectionExists()) {
  //      ListScrollingUtil.moveDown(myList, e.getModifiersEx());
  //    }
  //  }
  //  else if ("selectPreviousRow".equals(action)) {
  //    ListScrollingUtil.moveUp(myList, e.getModifiersEx());
  //  }
  //  else if ("scrollDown".equals(action)) {
  //    ListScrollingUtil.movePageDown(myList);
  //  }
  //  else if ("scrollUp".equals(action)) {
  //    ListScrollingUtil.movePageUp(myList);
  //  }
  //  else if (getSelectedFileFromCompletionPopup() != null && (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_TAB) && e.getModifiers() == 0) {
  //    hideCurrentPopup();
  //    e.consume();
  //    processChosenFromCompletion(true, e.getKeyCode() == KeyEvent.VK_TAB);
  //  }
  //}


  private void showCompletionPopup(final JList list, final String title) {
    //if (myCurrentPopup != null) {
    //  closePopup();
    //}

    final Runnable chooseCallback = new Runnable() {
      public void run() {
        final String selectedValue = (String)list.getSelectedValue();
        if (selectedValue != null) {
          setText(selectedValue);
        }
        IdeFocusManager.getInstance(myProject).requestFocus(TextFieldWithAutoCompletion.this, true);
      }
    };

    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }

    //builder.addListener(new JBPopupListener() {
    //  public void beforeShown(final Project project, final JBPopup popup) {
    //    TextFieldWithAutoCompletion.this
    //        .registerKeyboardAction(myCancelAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    //    for (Action each : myDisabledTextActions) {
    //      each.setEnabled(false);
    //    }
    //  }
    //
    //  public void onClosed(final JBPopup popup) {
    //    TextFieldWithAutoCompletion.this.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    //    for (Action each : myDisabledTextActions) {
    //      each.setEnabled(true);
    //    }
    //  }
    //});


    final JBPopup popup =
        builder.setMovable(false).setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(chooseCallback)
        // .setCancelCalllback(new Computable<Boolean>() {
        //  public Boolean compute() {
        //    final int i = getCaretPosition();
        //    setSelectionStart(i);
        //    setSelectionEnd(i);
        //    setFocusTraversalKeysEnabled(true);
        //    IdeFocusManager.getInstance(myProject)
        //        .requestFocus(TextFieldWithAutoCompletion.this, true);
        //    return Boolean.TRUE;
        //  }
        //})
        .setCancelKeyEnabled(false)
        .createPopup();
    //setFocusTraversalKeysEnabled(false);
    popup.showUnderneathOf(this);
    //myCurrentPopup.showUnderneathOf(this);
  }

  //private class CancelAction implements ActionListener {
  //  public void actionPerformed(final ActionEvent e) {
  //    if (myCurrentPopup != null) {
  //      //myAutopopup = false;
  //      hideCurrentPopup();
  //    }
  //  }
  //}

  // private void hideCurrentPopup() {
  //  if (myCurrentPopup != null) {
  //    myCurrentPopup.cancel();
  //    myCurrentPopup = null;
  //  }
  //
  //  //if (myNoSuggestionsPopup != null) {
  //  //  myNoSuggestionsPopup.cancel();
  //  //  myNoSuggestionsPopup = null;
  //  //}
  //}

  //private void closePopup() {
  //  hideCurrentPopup();
  //  //myCurrentCompletion = null;
  //}

}
