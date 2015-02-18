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
package com.jetbrains.python.suggestionList;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * List of suggestions to be displayed.
 * Suggestions may be separated into groups: Group1[suggestion1, suggestion2, suggestion3].
 * Each suggestion may also be marked as strong (see {@link com.jetbrains.python.suggestionList.Suggestion}
 *
 * @author Ilya.Kazakevich
 */
public class SuggestionList {
  /**
   * Listens popup close event
   */
  @Nullable
  private final JBPopupListener myListener;
  /**
   * Popup itself
   */
  @Nullable
  private JBPopup myListPopUp;
  /**
   * Model of suggestion list
   */
  private final DefaultListModel myListModel = new DefaultListModel();
  /**
   * Suggestion list
   */
  private final JList myList = new JList(myListModel);

  public SuggestionList() {
    myListener = null;
  }

  /**
   * @param listener popup listener (will be called back when popup closed)
   */
  public SuggestionList(@SuppressWarnings("NullableProblems") @NotNull final JBPopupListener listener) {
    myListener = listener;
  }

  /**
   * @param values          suggestions wrapped into suggestion builder.
   *                        Prefered usage pattern.
   * @param displayPoint    point on screen to display suggestions
   * @param elementToSelect element in list to be selected (if any)
   * @see com.jetbrains.python.suggestionList.SuggestionsBuilder
   */
  public void showSuggestions(@NotNull final SuggestionsBuilder values,
                              @NotNull final RelativePoint displayPoint,
                              @Nullable final String elementToSelect) {
    showSuggestions(values.getList(), displayPoint, elementToSelect);
  }

  /**
   * @param values          suggestions in format [group1[suggestions]]. See class doc for info about groups.
   *                        We recommend you <strong>not to use</strong> this method.
   *                        Use {@link #showSuggestions(SuggestionsBuilder, com.intellij.ui.awt.RelativePoint, String)} instead.
   *                        {@link com.jetbrains.python.suggestionList.SuggestionsBuilder} is easier to use
   * @param displayPoint    point on screen to display suggestions
   * @param elementToSelect element in list to be selected (if any)
   */
  public void showSuggestions(@NotNull final List<List<Suggestion>> values,
                              @NotNull final RelativePoint displayPoint,
                              @Nullable final String elementToSelect) {
    close();
    myList.setCellRenderer(new MyCellRenderer());
    myListModel.clear();
    if (values.isEmpty()) {
      return;
    }
    // Fill and select

    int record = 0; // Record to select
    // Iterate through groups adding suggestions. Odd groups should be marked differently.
    for (int groupId = 0; groupId < values.size(); groupId++) {
      final List<Suggestion> suggestions = values.get(groupId);
      for (int suggestionId = 0; suggestionId < suggestions.size(); suggestionId++) {
        final Suggestion suggestion = suggestions.get(suggestionId);
        myListModel.addElement(new SuggestionListElement((groupId % 2) == 0, suggestion));
        if (suggestion.getText().equals(elementToSelect)) {
          myList.setSelectedIndex(record);
        }
        record++;
      }
    }
    if ((myList.getSelectedIndex() ==-1) && (!myListModel.isEmpty())) {
      myList.setSelectedIndex(0); // Select first element
    }

    // Use scroll bars
    final JScrollPane content = new JScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    myListPopUp =
      JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
        .createPopup();
    if (myListener != null) {
      myListPopUp.addListener(myListener);
    }
    myListPopUp.show(displayPoint);
    myList.ensureIndexIsVisible(myList.getSelectedIndex()); // Scrolls to selected
  }


  /**
   * Close suggestion list
   */
  public void close() {
    if (myListPopUp != null) {
      myListPopUp.cancel();
    }
  }

  /**
   * Move selection
   *
   * @param directionUp up if true. Down otherwise
   */
  public void moveSelection(final boolean directionUp) {
    if (myListModel.isEmpty()) {
      return;
    }
    // Handle separation
    int newIndex = myList.getSelectedIndex() + (directionUp ? -1 : 1);
    if (newIndex < 0) {
      newIndex = 0;
    }
    if (newIndex >= myListModel.size()) {
      newIndex = myListModel.size() - 1;
    }
    myList.setSelectedIndex(newIndex);
    myList.scrollRectToVisible(myList.getCellBounds(newIndex, newIndex));
  }

  /**
   * @return currently selected value (null if nothing is selected)
   */
  @Nullable
  public String getValue() {
    if (myListPopUp == null || !myListPopUp.isVisible()) {
      return null; // Nothing is selected if list is invisible
    }
    final Object value = myList.getSelectedValue();
    return ((value == null) ? null : getElement(value).mySuggestion.getText());
  }

  /**
   * @return true if no suggestion list is displayed now.
   */
  public final synchronized boolean isClosed() {
    return myListPopUp == null || myListPopUp.isDisposed();
  }

  /**
   * Element that represents suggestion
   */
  private static final class SuggestionListElement {
    /**
     * is part of odd group
     */
    private final boolean myOddGroup;
    /**
     * suggestion itself
     */
    @NotNull
    private final Suggestion mySuggestion;

    private SuggestionListElement(final boolean oddGroup,
                                  @NotNull final Suggestion suggestion) {
      myOddGroup = oddGroup;
      mySuggestion = suggestion;
    }
  }

  /**
   * Cell renderer for suggestion
   */
  private static class MyCellRenderer extends DefaultListCellRenderer {
    @NotNull
    private static final Color ODD_GROUP_SELECTED_BACKGROUND_COLOR =
      EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);
    @NotNull
    private static final Color ODD_GROUP_BACKGROUND_COLOR =
      EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.TEARLINE_COLOR);

    @Override
    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      final SuggestionListElement element = getElement(value);
      // Out parent always uses label
      final Component component = super.getListCellRendererComponent(list, element.mySuggestion.getText(), index, isSelected,
                                                                     cellHasFocus);


      if (element.myOddGroup) {
        component.setBackground(isSelected ? ODD_GROUP_SELECTED_BACKGROUND_COLOR : ODD_GROUP_BACKGROUND_COLOR);
        final Font oldFont = component.getFont();
        component.setFont(new Font(oldFont.getName(), Font.ITALIC, oldFont.getSize()));
      }

      if (!(component instanceof JLabel)) {
        return component; // We can't change any property here
      }

      final JLabel label = (JLabel)component;
      if (element.mySuggestion.isStrong()) {
        // We use icons to display "strong" element
        label.setIcon(PlatformIcons.FOLDER_ICON);
      }


      return component;
    }
  }

  /**
   * Converts argument to {@link SuggestionListElement} which always should be.
   *
   * @param value argument to convert
   * @return converted argument
   */
  @NotNull
  private static SuggestionListElement getElement(final Object value) {
    assert value instanceof SuggestionListElement : "Value is not valid element: " + value;
    return (SuggestionListElement)value;
  }
}
