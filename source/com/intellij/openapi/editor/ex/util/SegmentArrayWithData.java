package com.intellij.openapi.editor.ex.util;

public class SegmentArrayWithData extends SegmentArray {
  private long[] myData;

  public SegmentArrayWithData() {
    myData = new long[INITIAL_SIZE];
  }

  public void setElementAt(int i, int startOffset, int endOffset, long data) {
    super.setElementAt(i, startOffset, endOffset);
    myData = relocateArray(myData, i+1);
    myData[i] = data;
  }

  public void remove(int startIndex, int endIndex) {
    myData = remove(myData, startIndex, endIndex);
    super.remove(startIndex, endIndex);
  }

  public void insert(SegmentArrayWithData segmentArray, int startIndex) {
    myData = insert(myData, segmentArray.myData, startIndex, segmentArray.getSegmentCount());
    super.insert(segmentArray, startIndex);
  }

  public long getSegmentData(int index) {
    if(index < 0 || index >= mySegmentCount) throw new IndexOutOfBoundsException("Wrong index: " + index);
    return myData[index];
  }

  public void setSegmentData(int index, long data) {
    if(index < 0 || index >= mySegmentCount) throw new IndexOutOfBoundsException("Wrong index: " + index);
    myData[index] = data;
  }
}

