/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;

public class RenameWrongRefAction implements IntentionAction {
  PsiReferenceExpression myRefExpr;
  private static final String INPUT_VARIABLE_NAME = "INPUTVAR";
  private static final String OTHER_VARIABLE_NAME = "OTHERVAR";

  public RenameWrongRefAction(PsiReferenceExpression refExpr) {
    myRefExpr = refExpr;
  }

  public String getText() {
    return "Rename Reference";
  }

  public String getFamilyName() {
    return "Rename Wrong Reference";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myRefExpr.isValid() || !myRefExpr.getManager().isInProject(myRefExpr)) return false;
    int offset = editor.getCaretModel().getOffset();
    PsiElement refName = myRefExpr.getReferenceNameElement();
    if (offset < refName.getTextRange().getStartOffset() ||
        offset > refName.getTextRange().getEndOffset()) return false;
    return !(myRefExpr.multiResolve(true).length > 0);
  }

  class ReferenceNameExpression implements Expression {

    class HammingComparator implements Comparator<LookupItem> {
      public int compare(LookupItem lookupItem1, LookupItem lookupItem2) {
        String s1 = lookupItem1.getLookupString(), s2 = lookupItem2.getLookupString();
        String refName = myRefExpr.getReferenceName();
        int diff1 = 0;
        for (int i = 0; i < Math.min(s1.length(), refName.length()); i++) {
          if (s1.charAt(i) != refName.charAt(i)) diff1++;
        }
        int diff2 = 0;
        for (int i = 0; i < Math.min(s2.length(), refName.length()); i++) {
          if (s2.charAt(i) != refName.charAt(i)) diff2++;
        }
        return diff1 - diff2;
      }
    }

    ReferenceNameExpression(LookupItem[] items) {
      myItems = items;
      Arrays.sort(myItems, new HammingComparator ());
    }

    LookupItem[] myItems;

    public Result calculateResult(ExpressionContext context) {
      if (myItems == null || myItems.length == 0) {
        return new TextResult(myRefExpr.getReferenceName());
      }
      return new TextResult(myItems[0].getLookupString());
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    public LookupItem[] calculateLookupItems(ExpressionContext context) {
      if (myItems == null || myItems.length == 1) return null;
      return myItems;
    }
  }

  private LookupItem[] collectItems() {
    LinkedHashSet<LookupItem> items = new LinkedHashSet<LookupItem>();
    boolean qualified = myRefExpr.getQualifierExpression() != null;

    if (!qualified && !(myRefExpr.getParent() instanceof PsiMethodCallExpression)) {
      PsiVariable[] vars = CreateFromUsageUtils.guessMatchingVariables(myRefExpr);
      for (int i = 0; i < vars.length; i++) {
        LookupItemUtil.addLookupItem(items, vars[i].getName(), "");
      }
    } else {
      class MyScopeProcessor extends BaseScopeProcessor {
        ArrayList<PsiElement> myResult = new ArrayList<PsiElement>();
        boolean myFilterMethods;
        boolean myFilterStatics = false;

        MyScopeProcessor(PsiReferenceExpression refExpression) {
          myFilterMethods = refExpression.getParent() instanceof PsiMethodCallExpression;
          PsiExpression qualifier = refExpression.getQualifierExpression();
          if (qualifier instanceof PsiReferenceExpression) {
            PsiElement e = ((PsiReferenceExpression) qualifier).resolve();
            myFilterStatics = e != null && e instanceof PsiClass;
          } else if (qualifier == null) {
            PsiModifierListOwner scope = PsiTreeUtil.getParentOfType(refExpression, PsiModifierListOwner.class);
            myFilterStatics = scope != null && scope.hasModifierProperty(PsiModifier.STATIC);
          }
        }

        public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
          if (element instanceof PsiNamedElement
              && element instanceof PsiModifierListOwner
              && myFilterMethods == element instanceof PsiMethod) {
            if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC) == myFilterStatics) {
              myResult.add(element);
            }
          }
          return true;
        }

        public PsiElement[] getVariants () {
          return myResult.toArray(new PsiElement[myResult.size()]);
        }
      }

      MyScopeProcessor processor = new MyScopeProcessor(myRefExpr);
      myRefExpr.processVariants(processor);
      final PsiElement[] variants = processor.getVariants();
      for (int i = 0; i < variants.length; i++) {
        LookupItemUtil.addLookupItem(items, ((PsiNamedElement) variants[i]).getName(), "");
      }
    }

    return items.toArray(new LookupItem[items.size()]);
  }

  public void invoke(Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    Class[] scopes = new Class[]{PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiField.class, PsiFile.class};
    PsiReferenceExpression[] refs = CreateFromUsageUtils.collectExpressions(myRefExpr, scopes);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      PsiElement element = PsiTreeUtil.getParentOfType(myRefExpr, scopes);
      LookupItem[] items = collectItems();
      ReferenceNameExpression refExpr = new ReferenceNameExpression(items);

      Document document = editor.getDocument();
      TemplateBuilder builder = new TemplateBuilder(element);
      for (int i = 0; i < refs.length; i++) {
        PsiReferenceExpression expr = refs[i];
        if (!expr.equals(myRefExpr)) {
          builder.replaceElement(expr.getReferenceNameElement(), OTHER_VARIABLE_NAME, INPUT_VARIABLE_NAME, false);
        } else {
          builder.replaceElement(expr.getReferenceNameElement(), INPUT_VARIABLE_NAME, refExpr, true);
        }
      }

      final float proportion = EditorUtil.calcVerticalScrollProportion(editor);
      editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());

      for (int i = refs.length - 1; i >= 0; i--) {
        TextRange range = refs[i].getReferenceNameElement().getTextRange();
        document.deleteString(range.getStartOffset(), range.getEndOffset());
      }

      Template template = builder.buildInlineTemplate();
      editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());

      TemplateManager.getInstance(project).startTemplate(editor, template,
              new TemplateStateListener() {
                public void templateFinished(Template template) {
                  TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
                  int offset = templateState.getVariableRange(INPUT_VARIABLE_NAME).getEndOffset();
                  editor.getCaretModel().moveToOffset(offset);
                  EditorUtil.setVerticalScrollProportion(editor, proportion);
                }
              });

      EditorUtil.setVerticalScrollProportion(editor, proportion);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
