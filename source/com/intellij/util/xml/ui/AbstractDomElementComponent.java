package com.intellij.util.xml.ui;

import com.intellij.j2ee.ui.CompositeCommittable;
import com.intellij.j2ee.ui.CommittablePanel;
import com.intellij.util.xml.DomElement;

/**
 * User: Sergey.Vasiliev
 * Date: Nov 18, 2005
 */
public abstract class AbstractDomElementComponent extends CompositeCommittable implements CommittablePanel {
  private final DomElement myDomElement;

  protected AbstractDomElementComponent(final DomElement domElement) {
    myDomElement = domElement;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }
}
