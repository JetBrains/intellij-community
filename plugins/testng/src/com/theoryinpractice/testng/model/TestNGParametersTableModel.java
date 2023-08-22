/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.theoryinpractice.testng.model;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.theoryinpractice.testng.TestngBundle;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TestNGParametersTableModel extends ListTableModel<Map.Entry<String, String>>
{

    private ArrayList<Map.Entry<String, String>> parameterList;

    public TestNGParametersTableModel() {
        super(
                new ColumnInfo<Map.Entry<String, String>, String>(TestngBundle.message("testng.parameters.table.model.name"))
                {
                    @Override
                    public String valueOf(Map.Entry<String, String> object) {
                        return object.getKey();
                    }

                    @Override
                    public TableCellEditor getEditor(final Map.Entry<String, String>  item) {
                        final JTextField textField = new JTextField();
                        textField.setBorder(BorderFactory.createLineBorder(JBColor.BLACK));
                        return new DefaultCellEditor(textField);
                    }
                },
                new ColumnInfo<Map.Entry<String, String>, String>(TestngBundle.message("testng.parameters.table.model.value"))
                {
                    @Override
                    public String valueOf(Map.Entry<String, String> object) {
                        return object.getValue();
                    }

                    @Override
                    public TableCellEditor getEditor(final Map.Entry<String, String>  item) {
                        final JTextField textField = new JTextField();
                        textField.setBorder(BorderFactory.createLineBorder(JBColor.BLACK));
                        return new DefaultCellEditor(textField);
                    }
                }
        );
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    public void setParameterList(ArrayList<Map.Entry<String, String>> parameterList) {
        this.parameterList = parameterList;
        setItems(parameterList);
    }

    public void addParameter() {
        Map<String, String> map = new HashMap<>();
        map.put("", "");
        parameterList.addAll(map.entrySet());
        setParameterList(parameterList);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Map.Entry<String, String> entry = parameterList.get(rowIndex);
        String key = entry.getKey();
        String value = entry.getValue();

        switch (columnIndex) {
          case 0 -> key = (String)aValue;
          case 1 -> value = (String)aValue;
        }

        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        parameterList.set(rowIndex, map.entrySet().iterator().next());
        setParameterList(parameterList);
    }

    public void removeProperty(int rowIndex) {
        parameterList.remove(rowIndex);
        setParameterList(parameterList);
    }
}