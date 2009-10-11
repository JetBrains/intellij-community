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

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 26, 2005
 * Time: 7:33:45 PM
 */
package com.theoryinpractice.testng.model;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TestNGParametersTableModel extends ListTableModel<Map.Entry>
{

    private ArrayList<Map.Entry> parameterList;

    public TestNGParametersTableModel() {
        super(
                new ColumnInfo("Name")
                {
                    public Object valueOf(Object object) {
                        Map.Entry entry = (Map.Entry) object;
                        return entry.getKey();
                    }
                },
                new ColumnInfo("Value")
                {
                    public Object valueOf(Object object) {
                        Map.Entry entry = (Map.Entry) object;
                        return entry.getValue();
                    }
                }
        );
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    public void setParameterList(ArrayList<Map.Entry> parameterList) {
        this.parameterList = parameterList;
        setItems(parameterList);
    }

    public void addParameter() {
        Map map = new HashMap();
        map.put("", "");
        parameterList.addAll(map.entrySet());
        setParameterList(parameterList);
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Map.Entry entry = parameterList.get(rowIndex);
        parameterList.remove(rowIndex);

        Object key = entry.getKey();
        Object value = entry.getValue();

        switch (columnIndex) {
            case 0:
                key = aValue;
                break;
            case 1:
                value = aValue;
                break;
        }

        Map map = new HashMap();
        map.put(key, value);
        parameterList.addAll(map.entrySet());
        setParameterList(parameterList);
    }

    public void removeProperty(int rowIndex) {
        parameterList.remove(rowIndex);
        setParameterList(parameterList);
    }
}