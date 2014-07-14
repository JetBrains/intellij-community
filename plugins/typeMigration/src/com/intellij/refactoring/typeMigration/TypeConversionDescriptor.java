package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Sep 28, 2004
 * Time: 7:13:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class TypeConversionDescriptor extends TypeConversionDescriptorBase {
  private static final Logger LOG = Logger.getInstance("#" + TypeConversionDescriptor.class.getName());

  private String myStringToReplace = null;
  private String myReplaceByString = "$";
  private PsiExpression myExpression;

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

  @Override
  public void replace(PsiExpression expression) {
    if (getExpression() != null) expression = getExpression();
    final Project project = expression.getProject();
    final ReplaceOptions options = new ReplaceOptions();
    options.setMatchOptions(new MatchOptions());
    final Replacer replacer = new Replacer(project, null);
    try {
      final String replacement = replacer.testReplace(expression.getText(), getStringToReplace(), getReplaceByString(), options);
      try {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression.replace(
          JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(replacement, expression)));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
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
