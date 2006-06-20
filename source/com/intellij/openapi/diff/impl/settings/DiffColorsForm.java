package com.intellij.openapi.diff.impl.settings;

import com.intellij.application.options.colors.ClickNavigator;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorAndFontPanel;
import com.intellij.application.options.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeSearchHelper;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.EditorPlace;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Comparator;

public class DiffColorsForm {
  private MergePanel2.AsComponent myMergePanelComponent;
  private LabeledComponent<ColorPanel> myBackgoundColorPanelComponent;
  private JList myOptionsList;
  private JComponent myWholePanel;
  private LabeledComponent<ColorPanel> myStripeMarkColorComponent;

  private static final Comparator<TextDiffType> TEXT_DIFF_TYPE_COMPARATOR = new Comparator<TextDiffType>() {
      public int compare(TextDiffType textDiffType, TextDiffType textDiffType1) {
        return textDiffType.getDisplayName().compareToIgnoreCase(textDiffType1.getDisplayName());
      }
    };
  private final SortedListModel<TextDiffType> myOptionsModel = new SortedListModel<TextDiffType>(TEXT_DIFF_TYPE_COMPARATOR);
  private final ListSelectionListener myOptionSelectionListener = new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          TextDiffType selection = getSelectedOption();
          ColorPanel background = getBackgroundColorPanel();
          ColorPanel stripeMark = getStripeMarkColorPanel();
          if (selection == null) {
            background.setEnabled(false);
            stripeMark.setEnabled(false);
          } else {
            background.setEnabled(true);
            stripeMark.setEnabled(true);
            MyColorAndFontDescription description = getSelectedDescription();
            if (description != null) {
              background.setSelectedColor(description.getBackgroundColor());
              stripeMark.setSelectedColor(description.getStripeMarkColor());
            }
          }
        }
      };
  private final MyTabImpl myMyTab;
  private final HashMap<String, MyColorAndFontDescription> myDescriptions = new HashMap<String,MyColorAndFontDescription>();
  private EditorColorsScheme myScheme = null;
  private boolean myDefault;

  private TextDiffType getSelectedOption() {
    return (TextDiffType)myOptionsList.getSelectedValue();
  }

  public DiffColorsForm() {
    myMyTab = new MyTabImpl(myWholePanel);

    myOptionsList.setCellRenderer(new OptionsReneder());
    myOptionsList.setModel(myOptionsModel);

    MergePanel2 mergePanel = getMergePanel();
    mergePanel.setEditorProperty(MergePanel2.LINE_NUMBERS, Boolean.FALSE);
    mergePanel.setEditorProperty(MergePanel2.LINE_MARKERS_AREA, Boolean.FALSE);
    mergePanel.setEditorProperty(MergePanel2.ADDITIONAL_LINES, 1);
    mergePanel.setEditorProperty(MergePanel2.ADDITIONAL_COLUMNS, 1);
    for (int i = 0; i < MergePanel2.EDITORS_COUNT; i++) {
      final EditorMouseListener motionListener = new EditorMouseListener(i);
      final EditorClickListener clickListener = new EditorClickListener(i);
      mergePanel.getEditorPlace(i).addListener(new EditorPlace.EditorListener() {
        public void onEditorCreated(EditorPlace place) {
          Editor editor = place.getEditor();
          editor.addEditorMouseMotionListener(motionListener);
          editor.addEditorMouseListener(clickListener);
          editor.getCaretModel().addCaretListener(clickListener);
        }

        public void onEditorReleased(Editor releasedEditor) {
          releasedEditor.removeEditorMouseMotionListener(motionListener);
          releasedEditor.removeEditorMouseListener(clickListener);
        }
      });
      Editor editor = mergePanel.getEditor(i);
      if (editor != null) {
        editor.addEditorMouseMotionListener(motionListener);
        editor.addEditorMouseListener(clickListener);
      }
    }
    ListSelectionModel selectionModel = myOptionsList.getSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectionModel.addListSelectionListener(myOptionSelectionListener);

    getBackgroundColorPanel().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        MyColorAndFontDescription selectedDescription = getSelectedDescription();
        ColorPanel colorPanel = getBackgroundColorPanel();
        if (!checkModifiableScheme()) {
          colorPanel.setSelectedColor(selectedDescription.getBackgroundColor());
          return;
        }
        selectedDescription.setBackgroundColor(colorPanel.getSelectedColor());
        selectedDescription.apply(myScheme);
        updatePreview();
      }
    });
    getStripeMarkColorPanel().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        MyColorAndFontDescription selectedDescription = getSelectedDescription();
        ColorPanel colorPanel = getStripeMarkColorPanel();
        if (!checkModifiableScheme()) {
          colorPanel.setSelectedColor(selectedDescription.getStripeMarkColor());
          return;
        }
        selectedDescription.setStripeMarkColor(colorPanel.getSelectedColor());
        selectedDescription.apply(myScheme);
        updatePreview();
      }
    });
  }

  private boolean checkModifiableScheme() {
    if (myDefault) ColorAndFontPanel.showReadOnlyMessage(myWholePanel);
    return !myDefault;
  }

  private MyColorAndFontDescription getSelectedDescription() {
    TextDiffType selection = getSelectedOption();
    if (selection == null) return null;
    return myDescriptions.get(selection.getAttributesKey().getExternalName());
  }

  private void updatePreview() {
    MergeList mergeList = getMergePanel().getMergeList();
    if (mergeList != null) mergeList.updateMarkup();
    myMergePanelComponent.repaint();
  }

  public void setMergeRequest(Project project) {
    getMergePanel().setDiffRequest(new SampleMerge(project));
  }

  private ColorPanel getBackgroundColorPanel() {
    return myBackgoundColorPanelComponent.getComponent();
  }

  private ColorPanel getStripeMarkColorPanel() {
    return myStripeMarkColorComponent.getComponent();
  }

  private MergePanel2 getMergePanel() {
    return myMergePanelComponent.getMergePanel();
  }

  public void setScheme(EditorColorsScheme scheme, boolean isDefault) {
    myDefault = isDefault;
    myScheme = scheme;
    getMergePanel().setColorScheme(scheme);
  }

  public void setColorScheme(final EditorColorsScheme highlighterSettings) {
    getMergePanel().setEditorProperty(MergePanel2.HIGHLIGHTER_SETTINGS, highlighterSettings);
  }

  public JComponent getComponent() {
    return myMyTab;
  }

  public static void addSchemeDescriptions(ArrayList<EditorSchemeAttributeDescriptor> descriptions, EditorColorsScheme scheme) {
    for (TextDiffType diffType : TextDiffType.MERGE_TYPES) {
      descriptions.add(new MyColorAndFontDescription(diffType, scheme));
    }
  }

  private class EditorMouseListener extends EditorMouseMotionAdapter {
    private final int myIndex;

    public EditorMouseListener(int index) {
      myIndex = index;
    }

    public void mouseMoved(EditorMouseEvent e) {
      MergePanel2 mergePanel = getMergePanel();
      Editor editor = mergePanel.getEditor(myIndex);
      if (MergeSearchHelper.findChangeAt(e, mergePanel, myIndex) != null) ClickNavigator.setHandCursor(editor);
    }
  }

  private class EditorClickListener extends EditorMouseAdapter implements CaretListener {
    private final int myIndex;

    public EditorClickListener(int i) {
      myIndex = i;
    }

    public void mouseClicked(EditorMouseEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }

    private void select(Change change) {
      if (change == null) return;
      ListScrollingUtil.selectItem(myOptionsList, change.getType().getTextDiffType());
    }

    public void caretPositionChanged(CaretEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }
  }

  private class MyTabImpl extends JPanel implements ColorAndFontPanel.MyTab {
    public MyTabImpl(JComponent component) {
      super(new BorderLayout());
      add(component, BorderLayout.CENTER);
    }

    public void fillOptionsList(ColorAndFontOptions options, String category) {
      myOptionsModel.clear();
      myDescriptions.clear();
      HashMap<TextAttributesKey, TextDiffType> typesByKey = ContainerUtil.assignKeys(TextDiffType.MERGE_TYPES.iterator(), TextDiffType.ATTRIBUTES_KEY);
      for (int i = 0; i < options.getCurrentDescriptions().length; i++) {
        EditorSchemeAttributeDescriptor description = options.getCurrentDescriptions()[i];
        TextAttributesKey type = TextAttributesKey.find(description.getType());
        if (description.getGroup() == ColorAndFontOptions.DIFF_GROUP &&
            typesByKey.keySet().contains(type)) {
          myOptionsModel.add(typesByKey.get(type));
          myDescriptions.put(type.getExternalName(), (MyColorAndFontDescription)description);
        }
      }
      ListScrollingUtil.ensureSelectionExists(myOptionsList);
    }

    public JList getOptionsList() {
      return myOptionsList;
    }

    public void processListValueChanged() {
      updatePreview();
    }

    public void updateDescription(ColorAndFontOptions options) {}
  }

  private static class OptionsReneder extends ColoredListCellRenderer {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      TextDiffType diffType = (TextDiffType)value;
      append(diffType.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private static class SampleMerge extends DiffRequest {
    public SampleMerge(Project project) {
      super(project);
    }

    public DiffContent[] getContents() {
      return new DiffContent[]{createContent(LEFT_TEXT), createContent(CENTER_TEXT), createContent(RIGHT_TEXT)};
    }

    private static SimpleContent createContent(String text) {
      return new SimpleContent(text, StdFileTypes.JAVA);
    }

    public String[] getContentTitles() { return new String[]{"", "", ""}; }
    public String getWindowTitle() { return DiffBundle.message("merge.color.options.dialog.title"); }
  }

  @NonNls private static final String LEFT_TEXT = "class MyClass {\n" +
                                                                            "  int value;\n" +
                                                                            "\n" +
                                                                            "  void leftOnly() {}\n" +
                                                                            "\n" +
                                                                            "  void foo() {\n" +
                                                                            "   // Left changes\n" +
                                                                            "  }\n" +
                                                                            "}";
  @NonNls private static final String CENTER_TEXT = "class MyClass {\n" +
                                            "  int value;\n" +
                                            "\n" +
                                            "  void foo() {\n" +
                                            "  }\n" +
                                            "\n" +
                                            "  void removedFromLeft() {}\n" +
                                            "}";
  @NonNls private static final String RIGHT_TEXT = "class MyClass {\n" +
                                           "  long value;\n" +
                                           "\n" +
                                           "  void foo() {\n" +
                                           "   // Left changes\n" +
                                           "  }\n" +
                                           "\n" +
                                           "  void removedFromLeft() {}\n" +
                                           "}";

  private static class MyColorAndFontDescription implements EditorSchemeAttributeDescriptor {
    private Color myBackgroundColor;
    private Color myStripebarColor;
    private Color myOriginalBackground;
    private Color myOriginalStripebar;
    private final EditorColorsScheme myScheme;
    private final TextDiffType myDiffType;

    public MyColorAndFontDescription(TextDiffType diffType, EditorColorsScheme scheme) {
      myScheme = scheme;
      myDiffType = diffType;
      TextAttributes attrs = diffType.getTextAttributes(myScheme);
      myBackgroundColor = attrs.getBackgroundColor();
      myStripebarColor = attrs.getErrorStripeColor();
      myOriginalBackground = myBackgroundColor;
      myOriginalStripebar = myStripebarColor;
    }

    public void apply(EditorColorsScheme scheme) {
      TextAttributesKey key = myDiffType.getAttributesKey();
      TextAttributes attrs = new TextAttributes(null, myBackgroundColor, null, EffectType.BOXED, TextAttributes.TRANSPARENT);
      attrs.setErrorStripeColor(myStripebarColor);
      scheme.setAttributes(key, attrs);
    }

    public String getGroup() {
      return ColorAndFontOptions.DIFF_GROUP;
    }

    public EditorColorsScheme getScheme() {
      return myScheme;
    }

    public String getType() {
      return myDiffType.getAttributesKey().getExternalName();
    }

    public boolean isModified() {
      TextAttributes attrs = myDiffType.getTextAttributes(myScheme);
      return !Comparing.equal(myOriginalBackground, attrs.getBackgroundColor()) ||
             !Comparing.equal(myOriginalStripebar, attrs.getErrorStripeColor());
    }

    public void setBackgroundColor(Color selectedColor) {
      myBackgroundColor = selectedColor;
    }

    public Color getBackgroundColor() {
      return myBackgroundColor;
    }

    public void setStripeMarkColor(Color selectedColor) {
      myStripebarColor = selectedColor;
    }

    public Color getStripeMarkColor() {
      return myStripebarColor;
    }
  }
}
