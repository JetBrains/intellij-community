package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ValueContainerImpl<Value> implements ValueContainer<Value>, Cloneable{
  private HashMap<Value, Object> myInputIdMapping;

  public ValueContainerImpl() {
    myInputIdMapping = new HashMap<Value, Object>(16, 0.98f);
  }
  
  public ValueContainerImpl(DataInput in, DataExternalizer<Value> externalizer) throws IOException {
    final int valuesCount = in.readInt();
    final float loadFactor = 0.98f;
    myInputIdMapping = new HashMap<Value, Object>((int)(valuesCount / loadFactor) + 1, loadFactor);
    for (int idx = 0; idx < valuesCount; idx++) {
      final Value value = externalizer.read(in);
      final int idCount = in.readInt();
      if (idCount == 1) {
        myInputIdMapping.put(value, in.readInt());
      }
      else if (idCount > 0){
        TIntHashSet idSet = new TIntHashSet((int)(idCount / loadFactor) + 1, loadFactor);
        for (int i = 0; i < idCount; i++) {
          idSet.add(in.readInt());
        }
        myInputIdMapping.put(value, idSet);
      }
    }
  }

  public void write(DataOutput out, DataExternalizer<Value> externalizer) throws IOException {
    out.writeInt(size());

    for (Value value : myInputIdMapping.keySet()) {
      externalizer.save(out, value);

      final IntIterator ids = getInputIdsIterator(value);
      if (ids != null) {
        out.writeInt(ids.size());
        while (ids.hasNext()) {
          out.writeInt(ids.next());
        }
      }
      else {
        out.writeInt(0);
      }
    }
  }
  
  public void addValue(int inputId, Value value) {
    final Object input = myInputIdMapping.get(value);
    if (input == null) {
      //idSet = new TIntHashSet(3, 0.98f);
      myInputIdMapping.put(value, new Integer(inputId));
    }
    else {
      final TIntHashSet idSet;
      if (input instanceof Integer) {
        idSet = new TIntHashSet(3, 0.98f);
        idSet.add(((Integer)input).intValue());
        myInputIdMapping.put(value, idSet);
      }
      else {
        idSet = (TIntHashSet)input;
      }
      idSet.add(inputId);
    }
  }

  public int size() {
    return myInputIdMapping.size();
  }

  public void removeValue(int inputId, Value value) {
    final Object input = myInputIdMapping.get(value);
    if (input instanceof TIntHashSet) {
      final TIntHashSet idSet = (TIntHashSet)input;
      idSet.remove(inputId);
      if (!idSet.isEmpty()) {
        return;
      }
    }
    myInputIdMapping.remove(value);
  }

  public Iterator<Value> getValueIterator() {
    return Collections.unmodifiableSet(myInputIdMapping.keySet()).iterator();
  }

  public List<Value> toValueList() {
    if (myInputIdMapping.isEmpty()) {
      return Collections.emptyList();
    }
    return new ArrayList<Value>(myInputIdMapping.keySet());
  }

  public int[] getInputIds(final Value value) {
    final Object input = myInputIdMapping.get(value);
    final int[] idSet;
    if (input instanceof TIntHashSet) {
      idSet = ((TIntHashSet)input).toArray();
    }
    else if (input instanceof Integer ){
      idSet = new int[] {((Integer)input).intValue()};
    }
    else {
      idSet = new int[0];
    }
    return idSet;
  }

  public IntIterator getInputIdsIterator(final Value value) {
    final Object input = myInputIdMapping.get(value);
    final IntIterator it;
    if (input instanceof TIntHashSet) {
      it = new IntSetIterator((TIntHashSet)input);
    }
    else if (input instanceof Integer ){
      it = new SingleValueIterator(((Integer)input).intValue());
    }
    else {
      it = EMPTY_ITERATOR;
    }
    return it;
  }

  public ValueContainerImpl<Value> clone() {
    try {
      final ValueContainerImpl clone = (ValueContainerImpl)super.clone();
      clone.myInputIdMapping = (HashMap<Value, Object>)myInputIdMapping.clone();
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public static final IntIterator EMPTY_ITERATOR = new IntIterator() {
    public boolean hasNext() {
      return false;
    }

    public int next() {
      return 0;
    }

    public int size() {
      return 0;
    }
  };
  
  private static class SingleValueIterator implements IntIterator {
    private int myValue;
    private boolean myValueRead = false;

    private SingleValueIterator(int value) {
      myValue = value;
    }

    public boolean hasNext() {
      return !myValueRead;
    }

    public int next() {
      try {
        return myValue;
      }
      finally {
        myValueRead = true;
      }
    }

    public int size() {
      return 1;
    }
  }

  private static class IntSetIterator implements IntIterator {
    private final TIntIterator mySetIterator;
    private final int mySize;

    public IntSetIterator(final TIntHashSet set) {
      mySetIterator = set.iterator();
      mySize = set.size();
    }

    public boolean hasNext() {
      return mySetIterator.hasNext();
    }

    public int next() {
      return mySetIterator.next();
    }

    public int size() {
      return mySize;
    }
  }
}
