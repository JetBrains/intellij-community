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

import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.11.2007
 */
public class AdvancedXsdOptions implements AdvancedOptions {
  @NonNls
  private static final String DISABLE_ABSTRACT_ELEMENTS = "disable-abstract-elements";
  @NonNls
  private static final String ANY_PROCESS_CONTENTS = "any-process-contents";
  @NonNls
  private static final String ANY_ATTRIBUTE_PROCESS_CONTENTS = "any-attribute-process-contents";

  private JComponent myRoot;

  private JCheckBox myDisableAbstractElements;
  private ComboBox myAnyProcessContents;
  private ComboBox myAnyAttributeProcessContents;

  @Override
  public JComponent getRoot() {
    return myRoot;
  }

  @Override
  public Map<String, ?> getOptions() {
    final Map<String, Object> strings = new HashMap<>();
    if (myDisableAbstractElements.isSelected()) {
      strings.put(DISABLE_ABSTRACT_ELEMENTS, Boolean.TRUE);
    }
    strings.put(ANY_PROCESS_CONTENTS, myAnyProcessContents.getSelectedItem());
    strings.put(ANY_ATTRIBUTE_PROCESS_CONTENTS, myAnyAttributeProcessContents.getSelectedItem());
    return strings;
  }

  @Override
  public void setOptions(Map<String, ?> inputOptions) {
    myDisableAbstractElements.setSelected(inputOptions.get(DISABLE_ABSTRACT_ELEMENTS) == Boolean.TRUE);
    final Object o = inputOptions.get(ANY_PROCESS_CONTENTS);
    if (o != null) {
      myAnyProcessContents.setSelectedItem(o);
    }
    final Object o2 = inputOptions.get(ANY_ATTRIBUTE_PROCESS_CONTENTS);
    if (o2 != null) {
      myAnyAttributeProcessContents.setSelectedItem(o2);
    }
  }
}
