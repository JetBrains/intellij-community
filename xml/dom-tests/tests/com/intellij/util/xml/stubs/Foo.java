package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.Stubbed;
import com.intellij.util.xml.DomElement;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 8/3/12
 */
public interface Foo extends DomElement {

  @Stubbed
  List<Bar> getBars();

  Bar addBar();
}
