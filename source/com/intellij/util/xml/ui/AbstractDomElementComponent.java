package com.intellij.util.xml.ui;

import com.intellij.j2ee.ui.CompositeCommittable;
import com.intellij.j2ee.ui.CommittablePanel;
import com.intellij.util.xml.DomElement;

/**
 * User: Sergey.Vasiliev
 * Date: Nov 18, 2005
 */
public abstract class AbstractDomElementComponent<T extends DomElement> extends CompositeCommittable implements CommittablePanel {
  protected T myDomElement;

  protected AbstractDomElementComponent(final T domElement) {
    myDomElement = domElement;
  }

  public T getDomElement() {
    return myDomElement;
  }
}
