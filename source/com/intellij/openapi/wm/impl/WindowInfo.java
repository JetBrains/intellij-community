package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import org.jdom.Element;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WindowInfo implements Cloneable,JDOMExternalizable{
  /**
   * XML tag.
   */
  static final String TAG="window_info";
  /**
   * Default window weight.
   */
  static final float DEFAULT_WEIGHT=.33f;

  private boolean myActive;
  private ToolWindowAnchor myAnchor;
  private boolean myAutoHide;
  /**
   * Bounds of window in "floating" mode. It equals to <code>null</code> if
   * floating bounds are undefined.
   */
  private Rectangle myFloatingBounds;
  private String myId;
  private ToolWindowType myInternalType;
  private ToolWindowType myType;
  private boolean myVisible;
  private float myWeight;
  /**
   * Defines order of tool window button inside the stripe.
   * The default value is <code>-1</code>.
   */
  private int myOrder;

  /**
   * Creates <code>WindowInfo</code> for tool window with wpecified <code>ID</code>.
   */
  WindowInfo(final String id){
    myActive=false;
    myAnchor=ToolWindowAnchor.LEFT;
    myAutoHide=false;
    myFloatingBounds=null;
    myId=id;
    setType(ToolWindowType.DOCKED);
    myVisible=false;
    myWeight=DEFAULT_WEIGHT;
    myOrder=-1;
  }

  /**
   * Creates copy of <code>WindowInfo</code> object.
   */
  public WindowInfo copy(){
    WindowInfo info=null;
    try{
      info=(WindowInfo)clone();
      if(myFloatingBounds!=null){
        info.myFloatingBounds=(Rectangle)myFloatingBounds.clone();
      }
    }catch(CloneNotSupportedException ignored){}
    return info;
  }

  /**
   * Copies all data from the passed <code>WindowInfo</code> into itself.
   */
  void copyFrom(final WindowInfo info){
    myActive=info.myActive;
    myAnchor=info.myAnchor;
    myAutoHide=info.myAutoHide;
    if(info.myFloatingBounds!=null){
      myFloatingBounds=(Rectangle)info.myFloatingBounds.clone();
    }else{
      myFloatingBounds=null;
    }
    myId=info.myId;
    myType=info.myType;
    myInternalType=info.myInternalType;
    myVisible=info.myVisible;
    myWeight=info.myWeight;
    myOrder=info.myOrder;
  }

  /**
   * @return tool window's anchor in internal mode.
   */
  ToolWindowAnchor getAnchor(){
    return myAnchor;
  }

  /**
   * @return bound of tool window in floating mode.
   */
  Rectangle getFloatingBounds(){
    return myFloatingBounds;
  }

  /**
   * @return <code>ID</code> of the tool window.
   */
  String getId(){
    return myId;
  }

  /**
   * @return type of the tool window in internal (docked or sliding) mode. Actually the tool
   * window can be in floating mode, but this method has sense if you want to know what type
   * tool window had when it was internal one. The method never returns <code>null</code>.
   */
  ToolWindowType getInternalType(){
    return myInternalType;
  }

  /**
   * @return current type of tool window.
   * @see com.intellij.openapi.wm.ToolWindowType#DOCKED
   * @see com.intellij.openapi.wm.ToolWindowType#FLOATING
   * @see com.intellij.openapi.wm.ToolWindowType#SLIDING
   */
  ToolWindowType getType(){
    return myType;
  }

  /**
   * @return internal weight of tool window. "weigth" means how much of internal desktop
   * area the tool window is occupied. The weight has sense if the tool window is docked or
   * sliding.
   */
  float getWeight(){
    return myWeight;
  }

  public int getOrder(){
    return myOrder;
  }

  public void setOrder(final int order){
    myOrder=order;
  }

  boolean isActive(){
    return myActive;
  }

  boolean isAutoHide(){
    return myAutoHide;
  }

  boolean isDocked(){
    return ToolWindowType.DOCKED==myType;
  }

  boolean isFloating(){
    return ToolWindowType.FLOATING==myType;
  }

  boolean isSliding(){
    return ToolWindowType.SLIDING==myType;
  }

  boolean isVisible(){
    return myVisible;
  }

  private static ToolWindowType parseToolWindowType(final String text){
    if(ToolWindowType.DOCKED.toString().equals(text)){
      return ToolWindowType.DOCKED;
    }else if(ToolWindowType.FLOATING.toString().equals(text)){
      return ToolWindowType.FLOATING;
    }else if(ToolWindowType.SLIDING.toString().equals(text)){
      return ToolWindowType.SLIDING;
    }else{
      throw new IllegalArgumentException();
    }
  }

  private static ToolWindowAnchor parseToolWindowAnchor(final String text){
    if(ToolWindowAnchor.TOP.toString().equals(text)){
      return ToolWindowAnchor.TOP;
    }else if(ToolWindowAnchor.LEFT.toString().equals(text)){
      return ToolWindowAnchor.LEFT;
    }else if(ToolWindowAnchor.BOTTOM.toString().equals(text)){
      return ToolWindowAnchor.BOTTOM;
    }else if(ToolWindowAnchor.RIGHT.toString().equals(text)){
      return ToolWindowAnchor.RIGHT;
    }else{
      throw new IllegalArgumentException();
    }
  }

  public void readExternal(final Element element){
    myId=element.getAttributeValue("id");
    try{
      myActive=Boolean.valueOf(element.getAttributeValue("active")).booleanValue();
    }catch(NumberFormatException ignored){}
    try{
      myAnchor=WindowInfo.parseToolWindowAnchor(element.getAttributeValue("anchor"));
    }catch(IllegalArgumentException ignored){}
      myAutoHide=Boolean.valueOf(element.getAttributeValue("auto_hide")).booleanValue();
    try{
      myInternalType=WindowInfo.parseToolWindowType(element.getAttributeValue("internal_type"));
    }catch(IllegalArgumentException ignored){}
    try{
      myType=parseToolWindowType(element.getAttributeValue("type"));
    }catch(IllegalArgumentException ignored){}
      myVisible=Boolean.valueOf(element.getAttributeValue("visible")).booleanValue();
    try{
      myWeight=Float.parseFloat(element.getAttributeValue("weight"));
    }catch(NumberFormatException ignored){}
    try{
      myOrder=Integer.valueOf(element.getAttributeValue("order")).intValue();
    }catch(NumberFormatException ignored){}
    try{
      myFloatingBounds=new Rectangle(
        Integer.parseInt(element.getAttributeValue("x")),
        Integer.parseInt(element.getAttributeValue("y")),
        Integer.parseInt(element.getAttributeValue("width")),
        Integer.parseInt(element.getAttributeValue("height"))
      );
    }catch(NumberFormatException ignored){}
  }

  /**
   * Sets new anchor.
   */
  void setAnchor(final ToolWindowAnchor anchor){
    if(anchor==null){
      throw new IllegalArgumentException("anchor cannot be null");
    }
    myAnchor=anchor;
  }

  void setActive(final boolean active){
    myActive=active;
  }

  void setAutoHide(final boolean autoHide){
    myAutoHide=autoHide;
  }

  void setFloatingBounds(final Rectangle floatingBounds){
    myFloatingBounds=floatingBounds;
  }

  void setType(final ToolWindowType type){
    if(type==null){
      throw new IllegalArgumentException("type cannot be null");
    }
    if(ToolWindowType.DOCKED==type||ToolWindowType.SLIDING==type){
      myInternalType=type;
    }
    myType=type;
  }

  void setVisible(final boolean visible){
    myVisible=visible;
  }

  /**
   * Sets window weight and adjust it to [0..1] range if necessary.
   */
  void setWeight(float weigth){
    if(weigth<.0f){
      weigth=.0f;
    }else if(weigth>1.0f){
      weigth=1.0f;
    }
    myWeight=weigth;
  }

  public void writeExternal(final Element element){
    element.setAttribute("id",myId);
    element.setAttribute("active",myActive?Boolean.TRUE.toString():Boolean.FALSE.toString());
    element.setAttribute("anchor",myAnchor.toString());
    element.setAttribute("auto_hide",myAutoHide?Boolean.TRUE.toString():Boolean.FALSE.toString());
    element.setAttribute("internal_type",myInternalType.toString());
    element.setAttribute("type",myType.toString());
    element.setAttribute("visible",myVisible?Boolean.TRUE.toString():Boolean.FALSE.toString());
    element.setAttribute("weight",Float.toString(myWeight));
    element.setAttribute("order",Integer.toString(myOrder));
    if(myFloatingBounds!=null){
      element.setAttribute("x",Integer.toString(myFloatingBounds.x));
      element.setAttribute("y",Integer.toString(myFloatingBounds.y));
      element.setAttribute("width",Integer.toString(myFloatingBounds.width));
      element.setAttribute("height",Integer.toString(myFloatingBounds.height));
    }
  }

  public boolean equals(final Object obj){
    if(!(obj instanceof WindowInfo)){
      return false;
    }
    final WindowInfo info=(WindowInfo)obj;
    if(
      myActive!=info.myActive||
      myAnchor!=info.myAnchor||
      !myId.equals(info.myId)||
      myAutoHide!=info.myAutoHide||
      !Comparing.equal(myFloatingBounds,info.myFloatingBounds)||
      myInternalType!=info.myInternalType||
      myType!=info.myType||
      myVisible!=info.myVisible||
      myWeight!=info.myWeight||
      myOrder!=info.myOrder
    ){
      return false;
    }else{
      return true;
    }
  }

  public int hashCode(){
    return myAnchor.hashCode()+myId.hashCode()+myType.hashCode()+myOrder;
  }

  public String toString(){
    final StringBuffer buffer=new StringBuffer();
    buffer.append(getClass().getName()).append('[');
    buffer.append("myId=").append(myId).append("; ");
    buffer.append("myVisible=").append(myVisible).append("; ");
    buffer.append("myActive=").append(myActive).append("; ");
    buffer.append("myAnchor=").append(myAnchor).append("; ");
    buffer.append("myOrder=").append(myOrder).append("; ");
    buffer.append("myAutoHide=").append(myAutoHide).append("; ");
    buffer.append("myWeight=").append(myWeight).append("; ");
    buffer.append("myType=").append(myType).append("; ");
    buffer.append("myInternalType=").append(myInternalType).append("; ");
    buffer.append("myFloatingBounds=").append(myFloatingBounds);
    buffer.append(']');
    return buffer.toString();
  }
}