/**
 * @author cdr
 */
package com.intellij.j2ee.module.view.common.editor;

import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Element;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SplitterProportionsData implements JDOMExternalizable{
  private List<Float> proportions = new ArrayList<Float>();
  private static final String DATA_VERSION = "1";

  public void saveSplitterProportions(Component root) {
    proportions.clear();
    doSaveSplitterProportions(root);
  }
  private void doSaveSplitterProportions(Component root) {
    if (root instanceof Splitter) {
      Float prop = new Float(((Splitter)root).getProportion());
      proportions.add(prop);
    }
    if (root instanceof Container) {
      Component[] children = ((Container)root).getComponents();
      for (int i = 0; i < children.length; i++) {
        Component child = children[i];
        doSaveSplitterProportions(child);
      }
    }
  }

  public void restoreSplitterProportions(Component root) {
    restoreSplitterProportions(root, 0);
  }

  private int restoreSplitterProportions(Component root, int index) {
    if (root instanceof Splitter) {
      if (proportions.size() <= index) return index;
      ((Splitter)root).setProportion(proportions.get(index++).floatValue());
    }
    if (root instanceof Container) {
      Component[] children = ((Container)root).getComponents();
      for (int i = 0; i < children.length; i++) {
        Component child = children[i];
        index = restoreSplitterProportions(child, index);
      }
    }
    return index;
  }

  public void readExternal(Element element) throws InvalidDataException {
    proportions.clear();
    String prop = element.getAttributeValue("proportions");
    String version = element.getAttributeValue("version");
    if (prop != null && Comparing.equal(version, DATA_VERSION)) {
      StringTokenizer tokenizer = new StringTokenizer(prop, ",");
      while (tokenizer.hasMoreTokens()) {
        String p = tokenizer.nextToken();
        proportions.add(Float.valueOf(p));
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    StringBuffer result = new StringBuffer();
    String sep = "";
    for (int i = 0; i < proportions.size(); i++) {
      Float proportion = proportions.get(i);
      result.append(sep);
      result.append(proportion);
      sep = ",";
    }
    element.setAttribute("proportions", result.toString());
    element.setAttribute("version", DATA_VERSION);
  }
}