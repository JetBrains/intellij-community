package com.intellij.refactoring.typeMigration;

import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Sep 28, 2004
 * Time: 7:13:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class TypeConversionDescriptor {

  private String myStringToReplace = null;
  private String myReplaceByString = "$";
  private PsiExpression myExpression;
  private TypeMigrationUsageInfo myRoot;

  public TypeConversionDescriptor() {
  }

  public TypeConversionDescriptor(@NonNls final String stringToReplace, @NonNls final String replaceByString) {
    myStringToReplace = stringToReplace;
    myReplaceByString = replaceByString;
  }

  public TypeConversionDescriptor(@NonNls final String stringToReplace, @NonNls final String replaceByString, final PsiExpression expression) {
    myStringToReplace = stringToReplace;
    myReplaceByString = replaceByString;
    myExpression = expression;
  }

  public void setStringToReplace(String stringToReplace) {
    myStringToReplace = stringToReplace;
  }

  public void setReplaceByString(String replaceByString) {
    myReplaceByString = replaceByString;
  }

  public String getStringToReplace() {
    return myStringToReplace;
  }

  public String getReplaceByString() {
    return myReplaceByString;
  }

  public PsiExpression getExpression() {
    return myExpression;
  }

  public void setExpression(final PsiExpression expression) {
    myExpression = expression;
  }

  public TypeMigrationUsageInfo getRoot() {
    return myRoot;
  }

  public void setRoot(final TypeMigrationUsageInfo root) {
    myRoot = root;
  }

  @Override
  public String toString() {
    StringBuffer buf = new StringBuffer();
    if (myReplaceByString != null) {
      buf.append(myReplaceByString);
    }
    if (myStringToReplace != null) {
      if (buf.length() > 0) buf.append(" ");
      buf.append(myStringToReplace);
    }
    if (myExpression != null) {
      if (buf.length() > 0) buf.append(" ");
      buf.append(myExpression.getText());
    }
    return buf.toString();
  }
}
