/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 9:39:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiVariable;
import gnu.trove.TIntStack;
import gnu.trove.TLongArrayList;
import gnu.trove.TLongHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Stack;

public class DfaMemoryStateImpl implements DfaMemoryState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl");
  private ArrayList<SortedIntSet> myEqClasses;
  private int myStateSize;
  private Stack myStack;
  private TIntStack myOffsetStack;
  private TLongHashSet myDistinctClasses;
  private com.intellij.util.containers.HashMap myVariableStates;

  private DfaMemoryStateImpl() {
  }

  public static DfaMemoryState createEmpty() {
    DfaMemoryStateImpl empty = new DfaMemoryStateImpl();

    empty.myEqClasses = new ArrayList<SortedIntSet>();
    empty.myStateSize = 0;
    empty.myStack = new Stack();
    empty.myDistinctClasses = new TLongHashSet();
    empty.myVariableStates = new com.intellij.util.containers.HashMap();
    empty.myOffsetStack = new TIntStack(1);
    return empty;
  }

  public DfaMemoryState createCopy() {
    DfaMemoryStateImpl newState = new DfaMemoryStateImpl();

    newState.myStack = (Stack) myStack.clone();
    newState.myDistinctClasses = new TLongHashSet(myDistinctClasses.toArray());
    newState.myEqClasses = new ArrayList<SortedIntSet>();
    newState.myStateSize = myStateSize;
    newState.myVariableStates = new com.intellij.util.containers.HashMap();
    newState.myOffsetStack = new TIntStack(myOffsetStack);

    for (int i = 0; i < myEqClasses.size(); i++) {
      SortedIntSet aClass = myEqClasses.get(i);
      newState.myEqClasses.add(aClass != null ? new SortedIntSet(aClass.toNativeArray()) : null);
    }

    try {
      for (Iterator iterator = myVariableStates.keySet().iterator(); iterator.hasNext();) {
        DfaVariableValue dfaVariableValue = (DfaVariableValue) iterator.next();
        newState.myVariableStates.put(dfaVariableValue, ((DfaVariableState) myVariableStates.get(dfaVariableValue)).clone());
      }
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return newState;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaMemoryState)) return false;
    DfaMemoryStateImpl that = (DfaMemoryStateImpl) obj;

    if (myStateSize != that.myStateSize) return false;
    if (myDistinctClasses.size() != that.myDistinctClasses.size()) return false;

    if (!myStack.equals(that.myStack)) return false;
    if (!myOffsetStack.equals(that.myOffsetStack)) return false;
    if (!myVariableStates.equals(that.myVariableStates)) return false;

    int[] permutation = getPermutationToSortedState();
    int[] thatPermutation = that.getPermutationToSortedState();

    for(int i = 0; i < myStateSize; i++) {
      SortedIntSet thisClass = myEqClasses.get(permutation[i]);
      SortedIntSet thatClass = that.myEqClasses.get(thatPermutation[i]);
      if (thisClass == null) break;
      if (thisClass.compareTo(thatClass) != 0) return false;
    }

    long[] pairs = getSortedDistinctClasses(permutation);
    long[] thatPairs = that.getSortedDistinctClasses(thatPermutation);

    for (int i = 0; i < pairs.length; i++) {
      if (pairs[i] != thatPairs[i]) {
        return false;
      }
    }

    return true;
  }

  private long[] getSortedDistinctClasses(int[] permutation) {
    long[] pairs = myDistinctClasses.toArray();
    for (int i = 0; i < pairs.length; i++) {
      pairs[i] = convert(pairs[i], permutation);
    }
    Arrays.sort(pairs);
    return pairs;
  }

  private long convert(long pair, int[] permutation) {
    if (myEqClasses.get(low(pair)) == null || myEqClasses.get(high(pair)) == null) {
      return -1L;
    }
    return createPair(inversePermutation(permutation, low(pair)), inversePermutation(permutation, high(pair)));
  }

  private static int inversePermutation(int[] permutation, int idx) {
    for (int i = 0; i < permutation.length; i++) {
      if (idx == permutation[i]) return i;
    }
    return -1;
  }

  private int[] getPermutationToSortedState() {
    int size = myEqClasses.size();
    int[] permutation = new int[size];
    for (int i = 0; i < size; i++) {
      permutation[i] = i;
    }

    for (int i = 0; i < permutation.length; i++) {
      for (int j = i + 1; j < permutation.length; j++) {
        if (compare(permutation[i], permutation[j]) > 0) {
          int t = permutation[i];
          permutation[i] = permutation[j];
          permutation[j] = t;
        }
      }
    }

    return permutation;
  }

  private int compare(int i1, int i2) {
    SortedIntSet s1 = myEqClasses.get(i1);
    SortedIntSet s2 = myEqClasses.get(i2);
    if (s1 == null && s2 == null) return 0;
    if (s1 == null) return 1;
    if (s2 == null) return -1;
    return s1.compareTo(s2);
  }

  public int hashCode() {
    return 0;
    //return myEqClasses.hashCode() + myStack.hashCode() + myVariableStates.hashCode();
  }

  private void appendClass(StringBuffer buf, int aClassIndex) {
    SortedIntSet aClass = myEqClasses.get(aClassIndex);
    if (aClass != null) {
      buf.append("(");
      int[] values = aClass.toNativeArray();
      for (int i = 0; i < values.length; i++) {
        if (i > 0) buf.append(", ");
        int value = values[i];
        DfaValue dfaValue = DfaValueFactory.getInstance().getValue(value);
        buf.append(dfaValue);
      }
      buf.append(")");
    }
  }

  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append('<');

    for (int i = 0; i < myEqClasses.size(); i++) {
      appendClass(result, i);
    }

    result.append(" distincts: ");
    long[] dclasses = myDistinctClasses.toArray();
    for (int i = 0; i < dclasses.length; i++) {
      long pair = dclasses[i];
      result.append("{");
      appendClass(result, low(pair));
      result.append(", ");
      appendClass(result, high(pair));
      result.append("} ");
    }

    result.append(" stack: ");
    for (int i = 0; i < myStack.size(); i++) {
      result.append(myStack.elementAt(i));
    }
    result.append('>');
    return result.toString();
  }

  public DfaValue pop() {
    return (DfaValue) myStack.pop();
  }

  public void push(DfaValue value) {
    myStack.push(value);
  }

  public int popOffset() {
    return myOffsetStack.pop();
  }

  public void pushOffset(int offset) {
    myOffsetStack.push(offset);
  }

  public void emptyStack() {
    myStack.removeAllElements();
  }

  public void setVarValue(DfaVariableValue var, DfaValue value) {
    flushVariable(var);
    if (value instanceof DfaUnknownValue) return;

    if (value instanceof DfaNewValue) {
      DfaTypeValue dfaType = DfaTypeValue.Factory.getInstance().create(((DfaNewValue)value).getType());
      DfaRelationValue dfaInstanceof = DfaRelationValue.Factory.getInstance().create(var, dfaType, "instanceof", false);
      DfaConstValue dfaNull = DfaConstValue.Factory.getInstance().getNull();
      DfaRelationValue dfaNotNull = DfaRelationValue.Factory.getInstance().create(var, dfaNull, "==", true);
      applyCondition(dfaInstanceof);
      applyCondition(dfaNotNull);
    } else if (value instanceof DfaTypeValue) {
      DfaRelationValue dfaInstanceof = DfaRelationValue.Factory.getInstance().create(var, value, "instanceof", false);
      applyInstanceofOrNull(dfaInstanceof);
    } else {
      DfaRelationValue dfaEqual = DfaRelationValue.Factory.getInstance().create(var, value, "==", false);
      applyCondition(dfaEqual);

      if (value instanceof DfaVariableValue) {
        try {
          DfaVariableState newState = (DfaVariableState) (getVariableState((DfaVariableValue) value)).clone();
          myVariableStates.put(var, newState);
        } catch (CloneNotSupportedException e) {
          LOG.error(e);
        }
      }
    }
  }

  private int getOrCreateEqClassIndex(DfaValue dfaValue) {
    for (int i = 0; i < myEqClasses.size(); i++) {
      SortedIntSet aClass = myEqClasses.get(i);
      if (aClass != null && aClass.contains(dfaValue.getID())) return i;
    }

    SortedIntSet aClass = new SortedIntSet();
    aClass.add(dfaValue.getID());
    myEqClasses.add(aClass);
    myStateSize++;

    return myEqClasses.size() - 1;
  }

  private int getEqClassIndex(DfaValue dfaValue) {
    for (int i = 0; i < myEqClasses.size(); i++) {
      SortedIntSet aClass = myEqClasses.get(i);
      if (aClass != null && aClass.contains(dfaValue.getID())) return i;
    }

    return -1;
  }

  private boolean unitClasses(int c1Index, int c2Index) {
    SortedIntSet c1 = myEqClasses.get(c1Index);
    SortedIntSet c2 = myEqClasses.get(c2Index);

    int nConst = 0;
    int[] c1s = c1.toNativeArray();
    for (int i = 0; i < c1s.length; i++) {
      DfaValue dfaValue = DfaValueFactory.getInstance().getValue(c1s[i]);
      if (dfaValue instanceof DfaConstValue) nConst++;
    }

    int[] c2s = c2.toNativeArray();
    for (int i = 0; i < c2s.length; i++) {
      DfaValue dfaValue = DfaValueFactory.getInstance().getValue(c2s[i]);
      if (dfaValue instanceof DfaConstValue) nConst++;
    }

    if (nConst > 1) return false;

    TLongArrayList c2Pairs = new TLongArrayList();
    long[] distincts = myDistinctClasses.toArray();
    for (int i = 0; i < distincts.length; i++) {
      int pc1 = low(distincts[i]);
      int pc2 = high(distincts[i]);
      boolean addedToC1 = false;

      if (pc1 == c1Index || pc2 == c1Index) {
        addedToC1 = true;
      }

      if (pc1 == c2Index || pc2 == c2Index) {
        if (addedToC1) return false;
        c2Pairs.add(distincts[i]);
      }
    }

    c1.add(c2.toNativeArray());
    long[] c2Array = c2Pairs.toNativeArray();
    myDistinctClasses.removeAll(c2Array);
    myEqClasses.set(c2Index,  null);
    myStateSize--;

    for (int i = 0; i < c2Array.length; i++) {
      long l = c2Array[i];
      myDistinctClasses.add(createPair(c1Index, low(l) == c2Index ? high(l) : low(l)));
    }

    return true;
  }

  private static int low(long l) {
    return (int) (l & 0xFFFFFFFF);
  }

  private static int high(long l) {
    return (int) ((l & 0xFFFFFFFF00000000L) >> 32);
  }

  private static long createPair(int i1, int i2) {
    if (i1 < i2) {
      long l = i1;
      l <<= 32;
      l += i2;
      return l;
    } else {
      long l = i2;
      l <<= 32;
      l += i1;
      return l;
    }
  }

  private void makeClassesDistinct(int c1Index, int c2Index) {
    myDistinctClasses.add(createPair(c1Index, c2Index));
  }

  public boolean isNull(DfaValue dfaValue) {
    if (dfaValue instanceof DfaVariableValue || dfaValue instanceof DfaConstValue) {
      DfaConstValue dfaNull = DfaConstValue.Factory.getInstance().getNull();
      int c1Index = getOrCreateEqClassIndex(dfaValue);
      int c2Index = getOrCreateEqClassIndex(dfaNull);

      return c1Index == c2Index;
    }

    return false;
  }

  public boolean isNotNull(DfaVariableValue dfaVar) {
    DfaConstValue dfaNull = DfaConstValue.Factory.getInstance().getNull();
    int c1Index = getOrCreateEqClassIndex(dfaVar);
    int c2Index = getOrCreateEqClassIndex(dfaNull);

    long[] pairs = myDistinctClasses.toArray();
    for (int i = 0; i < pairs.length; i++) {
      long pair = pairs[i];
      if (low(pair) == c1Index && high(pair) == c2Index ||
          high(pair) == c1Index && low(pair) == c2Index) {
        return true;
      }
    }

    return false;
  }

  public boolean applyInstanceofOrNull(DfaRelationValue dfaCond) {
    DfaVariableValue dfaVar = (DfaVariableValue) dfaCond.getLeftOperand();
    DfaTypeValue dfaType = (DfaTypeValue) dfaCond.getRightOperand();

    if (!isNotNull(dfaVar)) return true;
    return getVariableState(dfaVar).setInstanceofValue(dfaType);
  }

  public boolean applyCondition(DfaValue dfaCond) {
    if (dfaCond instanceof DfaUnknownValue) return true;
    if (dfaCond instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue) dfaCond;
      DfaVariableValue dfaNormalVar = dfaVar.isNegated() ? (DfaVariableValue) dfaVar.createNegated() : dfaVar;
      DfaConstValue dfaTrue = DfaConstValue.Factory.getInstance().getTrue();
      DfaRelationValue dfaEqualsTrue = DfaRelationValue.Factory.getInstance().create(dfaNormalVar, dfaTrue, "==", dfaVar.isNegated());
      return applyCondition(dfaEqualsTrue);
    }

    if (dfaCond instanceof DfaConstValue) {
      if (dfaCond == DfaConstValue.Factory.getInstance().getTrue()) return true;
      if (dfaCond == DfaConstValue.Factory.getInstance().getFalse()) return false;
      return true;
    }

    if (!(dfaCond instanceof DfaRelationValue)) {
      return true;
    }

    DfaRelationValue dfaRelation = (DfaRelationValue) dfaCond;
    DfaValue dfaLeft = dfaRelation.getLeftOperand();
    DfaValue dfaRight = dfaRelation.getRightOperand();

    if (dfaRight instanceof DfaTypeValue) {
      if (dfaLeft instanceof DfaVariableValue) {
        DfaVariableState varState = getVariableState((DfaVariableValue) dfaLeft);
        DfaVariableValue dfaVar = (DfaVariableValue) dfaLeft;
        if (dfaRelation.isNegated()) {
          return varState.addNotInstanceofValue((DfaTypeValue) dfaRight)
                 ? true
                 : applyCondition(compareToNull(dfaVar, false));
        }

        return applyCondition(compareToNull(dfaVar, true))
               ? varState.setInstanceofValue((DfaTypeValue) dfaRight)
               : false;

      }

      return true;
    }

    if (dfaLeft instanceof DfaUnknownValue || dfaRight instanceof DfaUnknownValue) {
      return true;
    }

    // DfaConstValue || DfaVariableValue
    int c1Index = getOrCreateEqClassIndex(dfaLeft);
    int c2Index = getOrCreateEqClassIndex(dfaRight);

    if (!dfaRelation.isNegated()) { //Equals
      if (c1Index == c2Index) return true;
      if (!unitClasses(c1Index, c2Index)) return false;
    } else { // Not Equals
      if (c1Index == c2Index) return false;
      makeClassesDistinct(c1Index, c2Index);
    }

    return true;
  }

  private DfaRelationValue compareToNull(DfaVariableValue dfaVar, boolean negated) {
    DfaConstValue dfaNull = DfaConstValue.Factory.getInstance().getNull();
    DfaRelationValue myDfaNotNull = DfaRelationValue.Factory.getInstance().create(dfaVar, dfaNull, "==", negated);
    return myDfaNotNull;
  }

  private DfaVariableState getVariableState(DfaVariableValue dfaVar) {
    DfaVariableState state = (DfaVariableState) myVariableStates.get(dfaVar);

    if (state == null) {
      state = new DfaVariableState();
      myVariableStates.put(dfaVar,  state);
      final PsiVariable psiVariable = dfaVar.getPsiVariable();
      if (psiVariable != null) {
        state.setInstanceofValue(DfaTypeValue.Factory.getInstance().create(psiVariable.getType()));
      }
    }

    return state;
  }

  public void flushFields(DataFlowRunner runner) {
    DfaVariableValue[] fields = runner.getFields();
    for (int i = 0; i < fields.length; i++) {
      DfaVariableValue field = fields[i];
      flushVariable(field);
    }
  }

  public void flushVariable(DfaVariableValue variable) {
    int varClassIndex = getEqClassIndex(variable);
    if (varClassIndex != -1) {
      SortedIntSet varClass = myEqClasses.get(varClassIndex);
      varClass.removeValue(variable.getID());

      if (varClass.size() == 0) {
        myEqClasses.set(varClassIndex, null);
        myStateSize--;
        long[] pairs = myDistinctClasses.toArray();
        for (int i = 0; i < pairs.length; i++) {
          long pair = pairs[i];
          if (low(pair)== varClassIndex || high(pair) == varClassIndex) {
            myDistinctClasses.remove(pair);
          }
        }
      }
    }

    myVariableStates.remove(variable);
  }
}
