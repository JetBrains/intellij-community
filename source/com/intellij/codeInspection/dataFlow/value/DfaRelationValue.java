/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 6, 2002
 * Time: 10:01:02 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class DfaRelationValue extends DfaValue {
  private DfaValue myLeftOperand;
  private DfaValue myRightOperand;
  private String myRelation;
  private boolean myIsNegated;

  public static class Factory {
    private static volatile Factory myInstance;
    private final DfaRelationValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaRelationValue>> myStringToObject;

    private Factory() {
      mySharedInstance = new DfaRelationValue();
      myStringToObject = new HashMap<String, ArrayList<DfaRelationValue>>();
    }

    public static Factory getInstance() {
      if (myInstance == null) {
        myInstance = new Factory();
      }
      return myInstance;
    }

    public static void freeInstance() {
      myInstance = null;
    }

    public DfaRelationValue create(DfaValue dfaLeft, DfaValue dfaRight, String relation, boolean negated) {
      if (dfaRight instanceof DfaTypeValue && !"instanceof".equals(relation)) return null;

      if (!(dfaLeft instanceof DfaVariableValue || dfaRight instanceof DfaVariableValue)) {
        return null;
      }

      if (!(dfaLeft instanceof DfaVariableValue)) {
        return create(dfaRight, dfaLeft, getSymmetricOperation(relation), negated);
      }

      // To canonical form.
      if ("!=".equals(relation)) {
        relation = "==";
        negated = !negated;
      }
      else if ("<".equals(relation)) {
        relation = ">=";
        negated = !negated;
      }
      else if ("<=".equals(relation)) {
        relation = ">";
        negated = !negated;
      }

      mySharedInstance.myLeftOperand = dfaLeft;
      mySharedInstance.myRightOperand = dfaRight;
      mySharedInstance.myRelation = relation;
      mySharedInstance.myIsNegated = negated;

      String id = mySharedInstance.toString();
      ArrayList<DfaRelationValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaRelationValue>();
        myStringToObject.put(id, conditions);
      }
      else {
        for (int i = 0; i < conditions.size(); i++) {
          DfaRelationValue rel = conditions.get(i);
          if (rel.hardEquals(mySharedInstance)) return rel;
        }
      }

      DfaRelationValue result = new DfaRelationValue(dfaLeft, dfaRight, relation, negated);
      conditions.add(result);
      return result;
    }

    private static String getSymmetricOperation(String sign) {
      if ("<".equals(sign)) {
        return ">";
      }
      else if (">=".equals(sign)) {
        return "<=";
      }
      else if (">".equals(sign)) {
        return "<";
      }
      else if ("<=".equals(sign)) {
        return ">=";
      }

      return sign;
    }
  }

  private DfaRelationValue() {
  }

  private DfaRelationValue(DfaValue myLeftOperand, DfaValue myRightOperand, String myRelation, boolean myIsNegated) {
    this.myLeftOperand = myLeftOperand;
    this.myRightOperand = myRightOperand;
    this.myRelation = myRelation;
    this.myIsNegated = myIsNegated;
  }

  public DfaValue getLeftOperand() {
    return myLeftOperand;
  }

  public DfaValue getRightOperand() {
    return myRightOperand;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  public DfaValue createNegated() {
    return Factory.getInstance().create(myLeftOperand, myRightOperand, myRelation, !myIsNegated);
  }

  private boolean hardEquals(DfaRelationValue rel) {
    return rel.myLeftOperand.equals(myLeftOperand) && rel.myRightOperand.equals(myRightOperand) &&
           rel.myRelation.equals(myRelation) &&
           rel.myIsNegated == myIsNegated;
  }

  public String toString() {
    return (isNegated() ? "not " : "") + myLeftOperand + myRelation + myRightOperand;
  }
}
