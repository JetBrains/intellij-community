package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadErrorComponent extends RadAtomicComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.RadErrorComponent");

  private final String myComponentClassName;
  private final Element myProperties;
  private final String myErrorDescription;

  public static RadErrorComponent create(
    final Module module,
    final String id,
    final String componentClassName,
    final Element properties,
    final String errorDescription
  ){
    return new RadErrorComponent(module, id, componentClassName, properties, errorDescription);
  }

  /**
   * @param properties can be <code>null</code>
   * @param errorDescription
   */
  private RadErrorComponent(
    final Module module,
    final String id,
    final String componentClassName,
    final Element properties,
    final String errorDescription
  ) {
    super(module, MyComponent.class, id);

    LOG.assertTrue(componentClassName != null);
    myComponentClassName = componentClassName;

    LOG.assertTrue(errorDescription != null);
    myErrorDescription = errorDescription;

    myProperties = properties;
  }

  public String getComponentClassName(){
    return myComponentClassName;
  }

  public String getErrorDescription() {
    return myErrorDescription;
  }

  public void write(final XmlWriter writer) {
    writer.startElement("component");
    try{
      writeId(writer);

      // write class
      writer.addAttribute("class", myComponentClassName);

      writeBinding(writer);
      writeConstraints(writer);

      // write properties (if any)
      if(myProperties != null){
        writer.writeElement(myProperties);
      }
    }finally{
      writer.endElement(); // component
    }
  }

  private static final class MyComponent extends JComponent{
    public MyComponent(){
      setMinimumSize(new Dimension(20, 20));
    }

    public void paint(final Graphics g){
      g.setColor(Color.red);
      g.fillRect(0,0,getWidth(),getHeight());
    }
  }
}
