package com.intellij.util.xml.stubs.model;

import com.intellij.util.xml.Stubbed;
import com.intellij.util.xml.DomElement;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 8/3/12
 */
@Stubbed
public interface Foo extends DomElement {

  @Stubbed
  List<Bar> getBars();

  Bar addBar();
}
