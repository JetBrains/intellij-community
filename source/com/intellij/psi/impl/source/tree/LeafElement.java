package com.intellij.psi.impl.source.tree;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.text.StringSearcher;

public abstract class LeafElement extends TreeElement {
  private volatile int myState = 0; // 16 bit for type, 15 bit for state and 1 bit for parentFlag
  public abstract char charAt(int position);
  public abstract int searchWord(int startOffset, StringSearcher searcher);
  public abstract int copyTo(char[] buffer, int start);

  public int copyTo(char[] buffer, int start, CharTable table){
    return copyTo(buffer, start);
  }

  public int getTextLength(CharTable table){
    return getTextLength();
  }

  protected LeafElement(IElementType type) {
    myState = myState | type.getIndex();
  }

  public final LeafElement findLeafElementAt(int offset) {
    return this;
  }

  public String getText() {
    return getText(SharedImplUtil.findCharTableByTree(this));  //To change body of implemented methods use File | Settings | File Templates.
  }

  public abstract int textMatches(CharSequence buffer, int start);

  public void registerInCharTable(CharTable table, CharTable oldCharTab) {}

  //boolean isLast = false;
  public synchronized void setState(int state){
    myState = myState & 0x8000FFFF | (((state + 1) & 0x7FFF) << 16);
  }

  public IElementType getElementType() {
    short sh = 0;
    sh |= (short)(myState & 0xFFFF);
    return IElementType.find(sh);
  }

  public int getState(){
    return (myState >> 16) & 0x7FFF - 1;
  }

  private boolean isLast(){
    return (myState & 0x80000000) != 0;
  }

  private void setLast(boolean last){
    if(last)
      myState |= 0x80000000;
    else
      myState &= 0x7FFFFFFF;
  }

  public synchronized TreeElement getTreeNext() {
    if(!isLast()) return next;
    return null;
  }

  public synchronized CompositeElement getTreeParent() {
    if(isLast())
      return (CompositeElement)next;
    TreeElement next = this.next;
    while(next instanceof LeafElement && !((LeafElement)next).isLast()) next = next.next;
    if(next != null) return next.getTreeParent();
    return null;
  }

  public synchronized TreeElement getTreePrev() {
    final CompositeElement parent = getTreeParent();
    if(parent == null) return null;
    TreeElement firstChild = parent.firstChild;
    if(firstChild == this) return null;
    while(firstChild != null && firstChild.next != this) firstChild = firstChild.next;
    return firstChild;
  }

  public synchronized void setTreeParent(CompositeElement parent) {
    if(next == null || isLast()){
      next = parent;
      setLast(true);
    }
  }

  public synchronized void setTreeNext(TreeElement next) {
    if(next != null){
      setLast(false);
      this.next = next;
    }
    else{
      if(!isLast()){
        this.next = getTreeParent();
        setLast(true);
      }
    }
  }

  public void setTreePrev(TreeElement prev) {
  }

  public Object clone() {
    LeafElement clone = (LeafElement)super.clone();
    clone.setState(-1);
    clone.setLast(false);
    return clone;
  }

  public abstract int getCharTabIndex();
}
