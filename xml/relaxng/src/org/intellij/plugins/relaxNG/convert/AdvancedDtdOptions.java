/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.convert;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlElementDecl;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.11.2007
 */
public class AdvancedDtdOptions implements AdvancedOptions {
  @NonNls
  private static final String COLON_REPLACEMENT = "colon-replacement";
  @NonNls
  private static final String ELEMENT_DEFINE = "element-define";
  @NonNls
  private static final String ATTLIST_DEFINE = "attlist-define";
  @NonNls
  private static final String INLINE_ATTLIST = "inline-attlist";
  @NonNls
  private static final String ANY_NAME = "any-name";
  @NonNls
  private static final String STRICT_ANY = "strict-any";
  @NonNls
  private static final String ANNOTATION_PREFIX = "annotation-prefix";
  @NonNls
  private static final String GENERATE_START = "generate-start";
  @NonNls
  private static final String XMLNS = "xmlns";

  private JComponent myRoot;

  private JCheckBox myInlineAttlistCheckBox;
  private JTextField myColonReplacement;
  private JTextField myElementDefine;
  private JTextField myAttlistDefine;
  private JTextField myAnyName;
  private JCheckBox myStrictAnyCheckBox;
  private JTextField myAnnotationPrefix;
  private JCheckBox myGenerateStartCheckBox;
  private JTextField myDefaultNS;
  private JTable myNamespaceMap;

  private JPanel myToolbar;

  public AdvancedDtdOptions() {
    myInlineAttlistCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          myAttlistDefine.setEnabled(false);
        } else {
          myAttlistDefine.setEnabled(true);
        }
      }
    });
    myNamespaceMap.setModel(new NamespaceMapModel());
    myNamespaceMap.getColumnModel().getColumn(0).setMaxWidth((int)(new JLabel("Prefix").getPreferredSize().width * 1.2));

    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction(null, "Remove Entry", AllIcons.General.Remove) {
      @Override
      public void update(AnActionEvent e) {
        if (myNamespaceMap.getModel().getRowCount() == 0 || myNamespaceMap.getSelectedRow() == -1) {
          e.getPresentation().setEnabled(false);
        } else {
          e.getPresentation().setEnabled(true);
        }
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        ((NamespaceMapModel)myNamespaceMap.getModel()).removeRow(myNamespaceMap.getSelectedRow());
      }
    });

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
    myToolbar.add(toolbar.getComponent());
  }

  @Override
  public JComponent getRoot() {
    return myRoot;
  }

  @Override
  public Map<String, ?> getOptions() {
    final HashMap<String, Object> map = new LinkedHashMap<>();

    map.put(INLINE_ATTLIST, myInlineAttlistCheckBox.isSelected());

    setText(map, COLON_REPLACEMENT, myColonReplacement);
    setText(map, ELEMENT_DEFINE, myElementDefine);
    setText(map, ATTLIST_DEFINE, myAttlistDefine);
    setText(map, ANY_NAME, myAnyName);

    if (myStrictAnyCheckBox.isSelected()) {
      map.put(STRICT_ANY, Boolean.TRUE);
    }

    setText(map, ANNOTATION_PREFIX, myAnnotationPrefix);

    map.put(GENERATE_START, myGenerateStartCheckBox.isSelected());

    if (myDefaultNS.getText().trim().length() > 0) {
      map.put(XMLNS, myDefaultNS.getText() );
    }

    final List<String[]> data = ((NamespaceMapModel)myNamespaceMap.getModel()).getData();
    for (String[] parts : data) {
      map.put(XMLNS + ":" + parts[0], parts[1]);
    }

    return map;
  }

  private static void setText(HashMap<String, Object> map, String option, JTextField field) {
    final String colonReplacement = field.getText();
    if (colonReplacement != null && colonReplacement.trim().length() > 0) {
      map.put(option, colonReplacement);
    }
  }

  @Override
  public void setOptions(Map<String, ?> inputOptions) {
    if (inputOptions.containsKey(COLON_REPLACEMENT)) {
      myColonReplacement.setText((String)inputOptions.get(COLON_REPLACEMENT));
    }
    myInlineAttlistCheckBox.setSelected(inputOptions.get(INLINE_ATTLIST) == Boolean.TRUE);

    if (inputOptions.containsKey(ELEMENT_DEFINE)) {
      myElementDefine.setText((String)inputOptions.get(ELEMENT_DEFINE));
    }
    if (inputOptions.containsKey(ATTLIST_DEFINE)) {
      myAttlistDefine.setText((String)inputOptions.get(ATTLIST_DEFINE));
    }
    if (inputOptions.containsKey(ANY_NAME)) {
      myAnyName.setText((String)inputOptions.get(ANY_NAME));
    }
    myStrictAnyCheckBox.setSelected(inputOptions.get(STRICT_ANY) == Boolean.TRUE);
    if (inputOptions.containsKey(ANNOTATION_PREFIX)) {
      myAnnotationPrefix.setText((String)inputOptions.get(ANNOTATION_PREFIX));
    }
    myGenerateStartCheckBox.setSelected(inputOptions.get(GENERATE_START) == Boolean.TRUE);
    if (inputOptions.containsKey(XMLNS)) {
      myDefaultNS.setText((String)inputOptions.get(XMLNS));
    }

    final NamespaceMapModel model = (NamespaceMapModel)myNamespaceMap.getModel();
    final Set<String> set = inputOptions.keySet();
    final String prefix = XMLNS + ":";
    for (String s : set) {
      if (s.startsWith(prefix)) {
        model.addMapping(s.substring(prefix.length()), (String)inputOptions.get(s));
      }
    }
  }

  public static Map<String, ?> prepareNamespaceMap(Project project, VirtualFile firstFile) {
    final PsiFile file = PsiManager.getInstance(project).findFile(firstFile);
    if (file == null) {
      return Collections.emptyMap();
    }

    final HashMap<String, Object> map = new LinkedHashMap<>();
    file.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof XmlElementDecl) {
          final String s = ((XmlElementDecl)element).getName();
          if (s != null) {
            final String[] parts = s.split(":");
            if (parts.length > 1) {
              map.put(XMLNS + ":" + parts[0], null);
            }
          }
        } else if (element instanceof XmlAttributeDecl) {
          final String s = ((XmlAttributeDecl)element).getName();
          if (s != null) {
            final String[] parts = s.split(":");
            if (parts.length > 1) {
              map.put(XMLNS + ":" + parts[0], null);
            }
          }
        }
        super.visitElement(element);
      }
    });

    return map;
  }

  private static class NamespaceMapModel extends AbstractTableModel {
    private final List<String[]> myList = new ArrayList<>();

    @Override
    public String getColumnName(int column) {
      return column == 0 ? "Prefix" : "URI";
    }

    @Override
    public int getRowCount() {
      return myList.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      myList.get(rowIndex)[columnIndex] = (String)aValue;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myList.get(rowIndex)[columnIndex];
    }

    public void addMapping(String prefix, String uri) {
      myList.add(new String[]{ prefix, uri });
      fireTableRowsInserted(myList.size() - 1, myList.size() - 1);
    }

    public void removeRow(int row) {
      myList.remove(row);
      fireTableRowsDeleted(row - 1, row - 1);
    }

    public List<String[]> getData() {
      return myList;
    }
  }
}
