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

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TestNGListenersTableModel extends AbstractListModel
{

  private final List<String> listenerList = new ArrayList<>();

  public int getSize() {
    return listenerList.size();
  }

  public Object getElementAt(int i) {
    return listenerList.get(i);
  }

  public void setListenerList(List<String> listenerList) {
    this.listenerList.clear();
    this.listenerList.addAll(listenerList);
    fireContentsChanged(this, 0, 0);
  }

  public List<String> getListenerList() {
    return listenerList;
  }

  public void addListener(String listener) {
    listenerList.add(listener);
    fireContentsChanged(this, 0, 0);
  }

  public void removeListener(int rowIndex) {
    listenerList.remove(rowIndex);
    fireContentsChanged(this, 0, 0);
  }
}
