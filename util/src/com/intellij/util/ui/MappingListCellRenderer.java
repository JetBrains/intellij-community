package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.util.Map;
import java.util.HashMap;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.09.2005
 * Time: 19:53:02
 * To change this template use File | Settings | File Templates.
 */
public class MappingListCellRenderer extends DefaultListCellRenderer {
  private Map<Object,String> myValueMap;

  public MappingListCellRenderer(final Map<Object, String> valueMap) {
    myValueMap = valueMap;
  }

  public MappingListCellRenderer(final Pair<Object, String>... valuePairs) {
    myValueMap = new HashMap<Object, String>();
    for(Pair<Object, String> valuePair: valuePairs) {
      myValueMap.put(valuePair.getFirst(), valuePair.getSecond());
    }
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    String newValue = myValueMap.get(value);
    if (newValue != null) {
      setText(newValue);
    }
    return this;
  }
}
