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
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;

public class ChainedConstructorTest {
  private JComponent myRootComponent;
  public JBScrollPane myScrollPane;
  public JList myList;

  public ChainedConstructorTest() {
    this(null, false);
  }

  public ChainedConstructorTest(String[] names, boolean mode) {
    myList = new JBList();
    myScrollPane.setViewportView(myList);
  }
}
