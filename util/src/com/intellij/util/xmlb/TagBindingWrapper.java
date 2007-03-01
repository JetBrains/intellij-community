package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;

//todo: merge with option tag binding
class TagBindingWrapper implements Binding {
  private Binding binding;
  private String myTagName;
  private String myAttributeName;

  public TagBindingWrapper(Binding binding, final String tagName, final String attributeName) {
    this.binding = binding;

    assert binding.getBoundNodeType().isAssignableFrom(Text.class);
    myTagName = tagName;
    myAttributeName = attributeName;
  }

  public Object serialize(Object o, Object context) {
    Element e = new Element(myTagName);
    Object n = binding.serialize(o, e);

    final String value = ((Content)n).getValue();

    if (myAttributeName.length() != 0) {
      e.setAttribute(myAttributeName, value);
    }
    else {
      e.addContent(new Text(value));
    }

    return e;
  }

  public Object deserialize(Object context, Object... nodes) {
    assert nodes.length == 1;

    Element e = (Element)nodes[0];
    final Object[] childNodes;
    if (myAttributeName.length() != 0) {
      childNodes = new Object[]{e.getAttribute(myAttributeName)};
    }
    else {
      childNodes = JDOMUtil.getContent(e);
    }
  
    return binding.deserialize(context, childNodes);
  }

  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myTagName);
  }

  public Class getBoundNodeType() {
    return Element.class;
  }

  public void init() {
  }
}
