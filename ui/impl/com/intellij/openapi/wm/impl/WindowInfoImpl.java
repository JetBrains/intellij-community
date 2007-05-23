package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WindowInfoImpl implements Cloneable,JDOMExternalizable, WindowInfo {
  /**
   * XML tag.
   */
  @NonNls static final String TAG="window_info";
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
  @NonNls protected static final String ID_ATTR = "id";
  @NonNls protected static final String ACTIVE_ATTR = "active";
  @NonNls protected static final String ANCHOR_ATTR = "anchor";
  @NonNls protected static final String AUTOHIDE_ATTR = "auto_hide";
  @NonNls protected static final String INTERNAL_TYPE_ATTR = "internal_type";
  @NonNls protected static final String TYPE_ATTR = "type";
  @NonNls protected static final String VISIBLE_ATTR = "visible";
  @NonNls protected static final String WEIGHT_ATTR = "weight";
  @NonNls protected static final String ORDER_ATTR = "order";
  @NonNls protected static final String X_ATTR = "x";
  @NonNls protected static final String Y_ATTR = "y";
  @NonNls protected static final String WIDTH_ATTR = "width";
  @NonNls protected static final String HEIGHT_ATTR = "height";

  private boolean myWasRead;

  /**
   * Creates <code>WindowInfo</code> for tool window with wpecified <code>ID</code>.
   */
  WindowInfoImpl(final String id){
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
  @SuppressWarnings({"EmptyCatchBlock"})
  public WindowInfoImpl copy(){
    WindowInfoImpl info=null;
    try{
      info=(WindowInfoImpl)clone();
      if(myFloatingBounds!=null){
        info.myFloatingBounds=(Rectangle)myFloatingBounds.clone();
      }
    }catch(CloneNotSupportedException ignored){}
    return info;
  }

  /**
   * Copies all data from the passed <code>WindowInfo</code> into itself.
   */
  void copyFrom(final WindowInfoImpl info){
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
  public ToolWindowAnchor getAnchor(){
    return myAnchor;
  }

  /**
   * @return bound of tool window in floating mode.
   */
  public Rectangle getFloatingBounds(){
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
  public ToolWindowType getType(){
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

  public boolean isActive(){
    return myActive;
  }

  public boolean isAutoHide(){
    return myAutoHide;
  }

  public boolean isDocked(){
    return ToolWindowType.DOCKED==myType;
  }

  public boolean isFloating(){
    return ToolWindowType.FLOATING==myType;
  }

  public boolean isSliding(){
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

  @SuppressWarnings({"EmptyCatchBlock"})
  public void readExternal(final Element element){
    myId=element.getAttributeValue(ID_ATTR);
    myWasRead = true;
    try{
      myActive=Boolean.valueOf(element.getAttributeValue(ACTIVE_ATTR)).booleanValue();
    }catch(NumberFormatException ignored){}
    try{
      myAnchor= parseToolWindowAnchor(element.getAttributeValue(ANCHOR_ATTR));
    }catch(IllegalArgumentException ignored){}
      myAutoHide=Boolean.valueOf(element.getAttributeValue(AUTOHIDE_ATTR)).booleanValue();
    try{
      myInternalType= parseToolWindowType(element.getAttributeValue(INTERNAL_TYPE_ATTR));
    }catch(IllegalArgumentException ignored){}
    try{
      myType=parseToolWindowType(element.getAttributeValue(TYPE_ATTR));
    }catch(IllegalArgumentException ignored){}
      myVisible=Boolean.valueOf(element.getAttributeValue(VISIBLE_ATTR)).booleanValue();
    try{
      myWeight=Float.parseFloat(element.getAttributeValue(WEIGHT_ATTR));
    }catch(NumberFormatException ignored){}
    try{
      myOrder=Integer.valueOf(element.getAttributeValue(ORDER_ATTR)).intValue();
    }catch(NumberFormatException ignored){}
    try{
      myFloatingBounds=new Rectangle(
        Integer.parseInt(element.getAttributeValue(X_ATTR)),
        Integer.parseInt(element.getAttributeValue(Y_ATTR)),
        Integer.parseInt(element.getAttributeValue(WIDTH_ATTR)),
        Integer.parseInt(element.getAttributeValue(HEIGHT_ATTR))
      );
    }catch(NumberFormatException ignored){}
  }

  /**
   * Sets new anchor.
   */
  void setAnchor(@NotNull final ToolWindowAnchor anchor){
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

  void setType(@NotNull final ToolWindowType type){
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
  void setWeight(float weight){
    if(weight<.0f){
      weight=.0f;
    }else if(weight>1.0f){
      weight=1.0f;
    }
    myWeight=weight;
  }

  public void writeExternal(final Element element){
    element.setAttribute(ID_ATTR,myId);
    element.setAttribute(ACTIVE_ATTR,myActive?Boolean.TRUE.toString():Boolean.FALSE.toString());
    element.setAttribute(ANCHOR_ATTR,myAnchor.toString());
    element.setAttribute(AUTOHIDE_ATTR,myAutoHide?Boolean.TRUE.toString():Boolean.FALSE.toString());
    element.setAttribute(INTERNAL_TYPE_ATTR,myInternalType.toString());
    element.setAttribute(TYPE_ATTR,myType.toString());
    element.setAttribute(VISIBLE_ATTR,myVisible?Boolean.TRUE.toString():Boolean.FALSE.toString());
    element.setAttribute(WEIGHT_ATTR,Float.toString(myWeight));
    element.setAttribute(ORDER_ATTR,Integer.toString(myOrder));
    if(myFloatingBounds!=null){
      element.setAttribute(X_ATTR,Integer.toString(myFloatingBounds.x));
      element.setAttribute(Y_ATTR,Integer.toString(myFloatingBounds.y));
      element.setAttribute(WIDTH_ATTR,Integer.toString(myFloatingBounds.width));
      element.setAttribute(HEIGHT_ATTR,Integer.toString(myFloatingBounds.height));
    }
  }

  public boolean equals(final Object obj){
    if(!(obj instanceof WindowInfoImpl)){
      return false;
    }
    final WindowInfoImpl info=(WindowInfoImpl)obj;
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

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  public boolean wasRead() {
    return myWasRead;
  }
}