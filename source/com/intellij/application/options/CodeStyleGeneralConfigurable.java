package com.intellij.application.options;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.ListUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Comparator;

public class CodeStyleGeneralConfigurable implements Configurable {
  JPanel myPanel;
  private JTextField myFieldPrefixField;
  private JTextField myStaticFieldPrefixField;
  private JTextField myParameterPrefixField;
  private JTextField myLocalVariablePrefixField;

  private JTextField myFieldSuffixField;
  private JTextField myStaticFieldSuffixField;
  private JTextField myParameterSuffixField;
  private JTextField myLocalVariableSuffixField;

  private JCheckBox myCbPreferLongerNames;
  private JCheckBox myCbLineCommentAtFirstColumn;
  private JCheckBox myCbBlockCommentAtFirstColumn;


  private MembersOrderList myMembersOrderList;
  private JScrollPane myMembersListScroll;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;

  private CodeStyleSettings mySettings;
  private JCheckBox myCbGenerateFinalParameters;
  private JCheckBox myCbGenerateFinalLocals;

  public CodeStyleGeneralConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
    myMembersOrderList = new MembersOrderList();
  }

  public JComponent createComponent() {
    myMembersListScroll.getViewport().add(myMembersOrderList);

    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsUp(myMembersOrderList);
      }
    });

    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsDown(myMembersOrderList);
      }
    });
    return myPanel;
  }

  public void disposeUIResources() {
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.code.generation");
  }

  public String getHelpTopic() {
    return "preferences.sourceCode.codeGeneration";
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }


  /*private JPanel createNamingPanel() {
    OptionGroup optionGroup = new OptionGroup("Naming");

    myCbPreferLongerNames = new JCheckBox("Prefer longer names");

    optionGroup.add(myCbPreferLongerNames);

    optionGroup.add(new JLabel("Name prefix for:"));

    myFieldPrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Field"), myFieldPrefixField, true);

    myStaticFieldPrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Static field"), myStaticFieldPrefixField, true);

    myParameterPrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Parameter"), myParameterPrefixField, true);

    myLocalVariablePrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Local variable"), myLocalVariablePrefixField, true);

    optionGroup.add(new JLabel("Name suffix for:"));

    myFieldSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Field"), myFieldSuffixField, true);

    myStaticFieldSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Static field"), myStaticFieldSuffixField, true);

    myParameterSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Parameter"), myParameterSuffixField, true);

    myLocalVariableSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Local variable"), myLocalVariableSuffixField, true);

    return optionGroup.createPanel();
  }

  private JPanel createCommentPanel() {
    OptionGroup optionGroup = new OptionGroup("Comment Code");

    myCbLineCommentAtFirstColumn = new JCheckBox("Line comment at first column");
    optionGroup.add(myCbLineCommentAtFirstColumn);

    myCbBlockCommentAtFirstColumn = new JCheckBox("Block comment at first column");
    optionGroup.add(myCbBlockCommentAtFirstColumn);

    return optionGroup.createPanel();
  }

  private JPanel createRightMarginPanel() {
    OptionGroup optionGroup = new OptionGroup("Wrapping ");

    myRightMarginField = new JTextField(4);
    optionGroup.add(new JLabel("Right margin (columns)") ,myRightMarginField);

    return optionGroup.createPanel();
  }

  private JPanel createLineSeparatorPanel(){
    OptionGroup optionGroup = new OptionGroup("Line Separator (for new files) ");


    myLineSeparatorCombo = new JComboBox();
    myLineSeparatorCombo.addItem(SYSTEM_DEPENDANT_STRING);
    myLineSeparatorCombo.addItem(UNIX_STRING);
    myLineSeparatorCombo.addItem(WINDOWS_STRING);
    myLineSeparatorCombo.addItem(MACINTOSH_STRING);

    optionGroup.add(myLineSeparatorCombo);

    return optionGroup.createPanel();
  }

  private JPanel createKeepWhenReformattingPanel() {
    OptionGroup optionGroup = new OptionGroup("Keep When Reformatting");

    myCbKeepLineBreaks = new JCheckBox("Line breaks");
    optionGroup.add(myCbKeepLineBreaks);

    myCbKeepFirstColumnComment = new JCheckBox("Comment at first column");
    optionGroup.add(myCbKeepFirstColumnComment);

    myCbKeepControlStatementInOneLine = new JCheckBox("Control statement in one line");
    optionGroup.add(myCbKeepControlStatementInOneLine);

    return optionGroup.createPanel();
  }

  private JPanel createMembersOrderPanel() {

    OptionGroup optionGroup = new OptionGroup("Order of Members");

    JPanel panel = new JPanel(new GridBagLayout());

    myMembersOrderList = new MembersOrderList();
    panel.add(new JScrollPane(myMembersOrderList), new GridBagConstraints(0,0,1,2,1,1,GridBagConstraints.NORTH,GridBagConstraints.BOTH,new Insets(0,0,0,0), 0,0));

    JButton moveUpButton = new JButton("Move Up");

    moveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e){
        ListUtil.moveSelectedItemsUp(myMembersOrderList);
      }
    });
    panel.add(moveUpButton, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.NORTH,GridBagConstraints.HORIZONTAL,new Insets(0,5,5,0), 0,0));

   JButton movDownButton = new JButton("Move Down");
    moveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e){
        ListUtil.moveSelectedItemsDown(myMembersOrderList);
      }
    });
   panel.add(movDownButton, new GridBagConstraints(1,1,1,1,0,1,GridBagConstraints.NORTH,GridBagConstraints.HORIZONTAL,new Insets(0,5,5,0), 0,0));

    optionGroup.add(panel);

    return optionGroup.createPanel();
  }*/

  public void reset() {
    myCbPreferLongerNames.setSelected(mySettings.PREFER_LONGER_NAMES);

    myFieldPrefixField.setText("" + mySettings.FIELD_NAME_PREFIX);
    myStaticFieldPrefixField.setText("" + mySettings.STATIC_FIELD_NAME_PREFIX);
    myParameterPrefixField.setText("" + mySettings.PARAMETER_NAME_PREFIX);
    myLocalVariablePrefixField.setText("" + mySettings.LOCAL_VARIABLE_NAME_PREFIX);

    myFieldSuffixField.setText("" + mySettings.FIELD_NAME_SUFFIX);
    myStaticFieldSuffixField.setText("" + mySettings.STATIC_FIELD_NAME_SUFFIX);
    myParameterSuffixField.setText("" + mySettings.PARAMETER_NAME_SUFFIX);
    myLocalVariableSuffixField.setText("" + mySettings.LOCAL_VARIABLE_NAME_SUFFIX);

    myCbLineCommentAtFirstColumn.setSelected(mySettings.LINE_COMMENT_AT_FIRST_COLUMN);
    myCbBlockCommentAtFirstColumn.setSelected(mySettings.BLOCK_COMMENT_AT_FIRST_COLUMN);

    myCbGenerateFinalLocals.setSelected(mySettings.GENERATE_FINAL_LOCALS);
    myCbGenerateFinalParameters.setSelected(mySettings.GENERATE_FINAL_PARAMETERS);
    myMembersOrderList.reset(mySettings);
  }

  public void apply() {
    mySettings.PREFER_LONGER_NAMES = myCbPreferLongerNames.isSelected();

    mySettings.FIELD_NAME_PREFIX = myFieldPrefixField.getText().trim();
    mySettings.STATIC_FIELD_NAME_PREFIX = myStaticFieldPrefixField.getText().trim();
    mySettings.PARAMETER_NAME_PREFIX = myParameterPrefixField.getText().trim();
    mySettings.LOCAL_VARIABLE_NAME_PREFIX = myLocalVariablePrefixField.getText().trim();

    mySettings.FIELD_NAME_SUFFIX = myFieldSuffixField.getText().trim();
    mySettings.STATIC_FIELD_NAME_SUFFIX = myStaticFieldSuffixField.getText().trim();
    mySettings.PARAMETER_NAME_SUFFIX = myParameterSuffixField.getText().trim();
    mySettings.LOCAL_VARIABLE_NAME_SUFFIX = myLocalVariableSuffixField.getText().trim();

    mySettings.LINE_COMMENT_AT_FIRST_COLUMN = myCbLineCommentAtFirstColumn.isSelected();
    mySettings.BLOCK_COMMENT_AT_FIRST_COLUMN = myCbBlockCommentAtFirstColumn.isSelected();

    mySettings.GENERATE_FINAL_LOCALS = myCbGenerateFinalLocals.isSelected();
    mySettings.GENERATE_FINAL_PARAMETERS = myCbGenerateFinalParameters.isSelected();

    myMembersOrderList.apply(mySettings);

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      DaemonCodeAnalyzer.getInstance(projects[i]).settingsChanged();
    }
  }

  public boolean isModified() {
    boolean isModified = false;
    isModified |= isModified(myCbPreferLongerNames, mySettings.PREFER_LONGER_NAMES);

    isModified |= isModified(myFieldPrefixField, mySettings.FIELD_NAME_PREFIX);
    isModified |= isModified(myStaticFieldPrefixField, mySettings.STATIC_FIELD_NAME_PREFIX);
    isModified |= isModified(myParameterPrefixField, mySettings.PARAMETER_NAME_PREFIX);
    isModified |= isModified(myLocalVariablePrefixField, mySettings.LOCAL_VARIABLE_NAME_PREFIX);

    isModified |= isModified(myFieldSuffixField, mySettings.FIELD_NAME_SUFFIX);
    isModified |= isModified(myStaticFieldSuffixField, mySettings.STATIC_FIELD_NAME_SUFFIX);
    isModified |= isModified(myParameterSuffixField, mySettings.PARAMETER_NAME_SUFFIX);
    isModified |= isModified(myLocalVariableSuffixField, mySettings.LOCAL_VARIABLE_NAME_SUFFIX);

    isModified |= isModified(myCbLineCommentAtFirstColumn, mySettings.LINE_COMMENT_AT_FIRST_COLUMN);
    isModified |= isModified(myCbBlockCommentAtFirstColumn, mySettings.BLOCK_COMMENT_AT_FIRST_COLUMN);


    isModified |= isModified(myCbGenerateFinalLocals, mySettings.GENERATE_FINAL_LOCALS);
    isModified |= isModified(myCbGenerateFinalParameters, mySettings.GENERATE_FINAL_PARAMETERS);

    isModified |= myMembersOrderList.isModified(mySettings);

    return isModified;
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, String value) {
    return !textField.getText().trim().equals(value);
  }

  private static class MembersOrderList extends JList {
    private static final String FIELDS = ApplicationBundle.message("listbox.members.order.fields");
    private static final String METHODS = ApplicationBundle.message("listbox.members.order.methods");
    private static final String CONSTRUCTORS = ApplicationBundle.message("listbox.members.order.constructors");
    private static final String INNER_CLASSES = ApplicationBundle.message("listbox.members.order.inner.classes");

    private DefaultListModel myModel;

    public MembersOrderList() {
      DefaultListModel model = new DefaultListModel();
      myModel = model;
      setModel(myModel);
      setVisibleRowCount(4);
    }

    public void reset(final CodeStyleSettings settings) {
      myModel.removeAllElements();
      String[] strings = getStrings(settings);
      for (int i = 0; i < strings.length; i++) {
        myModel.addElement(strings[i]);
      }

      setSelectedIndex(0);
    }

    private String[] getStrings(final CodeStyleSettings settings) {
      String[] strings = new String[]{FIELDS, METHODS, CONSTRUCTORS, INNER_CLASSES};

      Arrays.sort(strings, new Comparator() {
        public int compare(Object o1, Object o2) {
          int weight1 = getWeight(o1);
          int weight2 = getWeight(o2);
          return weight1 - weight2;
        }

        private int getWeight(Object o) {
          if (FIELDS.equals(o)) {
            return settings.FIELDS_ORDER_WEIGHT;
          }
          else if (METHODS.equals(o)) {
            return settings.METHODS_ORDER_WEIGHT;
          }
          else if (CONSTRUCTORS.equals(o)) {
            return settings.CONSTRUCTORS_ORDER_WEIGHT;
          }
          else if (INNER_CLASSES.equals(o)) {
            return settings.INNER_CLASSES_ORDER_WEIGHT;
          }
          else {
            throw new IllegalArgumentException("unexpected " + o);
          }
        }
      });
      return strings;
    }

    public void apply(CodeStyleSettings settings) {
      for (int i = 0; i < myModel.size(); i++) {
        Object o = myModel.getElementAt(i);
        int weight = i + 1;

        if (FIELDS.equals(o)) {
          settings.FIELDS_ORDER_WEIGHT = weight;
        }
        else if (METHODS.equals(o)) {
          settings.METHODS_ORDER_WEIGHT = weight;
        }
        else if (CONSTRUCTORS.equals(o)) {
          settings.CONSTRUCTORS_ORDER_WEIGHT = weight;
        }
        else if (INNER_CLASSES.equals(o)) {
          settings.INNER_CLASSES_ORDER_WEIGHT = weight;
        }
        else {
          throw new IllegalArgumentException("unexpected " + o);
        }
      }
    }

    public boolean isModified(CodeStyleSettings settings) {
      String[] oldStrings = getStrings(settings);
      String[] newStrings = new String[myModel.size()];
      for (int i = 0; i < newStrings.length; i++) {
        newStrings[i] = (String)myModel.getElementAt(i);
      }

      return !Arrays.equals(newStrings, oldStrings);
    }
  }
}