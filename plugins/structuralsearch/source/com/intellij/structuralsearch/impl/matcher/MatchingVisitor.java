package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.psi.javadoc.*;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import com.intellij.structuralsearch.impl.matcher.iterators.*;
import com.intellij.structuralsearch.impl.matcher.handlers.Handler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.predicates.ExprTypePredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.openapi.util.text.StringUtil;

import java.util.*;

/**
 * Visitor class to manage pattern matching
 */
@SuppressWarnings({"RefusedBequest"})
public class MatchingVisitor extends PsiElementVisitor {

  // the pattern element for visitor check
  protected PsiElement element;
  // the result of matching in visitor
  protected boolean result;

  // context of matching
  protected MatchContext matchContext;

  public void visitComment(PsiComment comment) {
    PsiElement comment2 = null;

    if (!(element instanceof PsiComment)) {
      if (element instanceof PsiMember) {
        final PsiElement[] children = element.getChildren();
        if (children[0] instanceof PsiComment) {
          comment2 = children[0];
        }
      }
    } else {
      comment2 = element;
    }

    if (comment2 == null) {
      result = false;
      return;
    }

    final Object userData = comment.getUserData(CompiledPattern.HANDLER_KEY);

    if (userData instanceof String) {
      String str = (String) userData;
      int end = comment2.getTextLength();

      if (((PsiComment)comment2).getTokenType() == JavaTokenType.C_STYLE_COMMENT) {
        end -= 2;
      }
      result = ((SubstitutionHandler)matchContext.getPattern().getHandler(str)).handle(
        comment2,
        2,
        end,
        matchContext
      );
    } else if (userData instanceof Handler) {
      result = ((Handler)userData).match(comment,comment2,matchContext);
    } else {
      result = comment.getText().equals(comment2.getText());
    }
  }

  public void visitDocTagValue(final PsiDocTagValue value) {
    final PsiDocTagValue value2 = (PsiDocTagValue) element;
    final boolean isTypedVar = matchContext.getPattern().isTypedVar(value);

    if (isTypedVar) {
      result = handleTypedElement(value,value2);
    } else {
      result = value.textMatches(value2);
    }
  }

  public void visitDocTag(final PsiDocTag tag) {
    final PsiDocTag tag2 = (PsiDocTag) element;
    final boolean isTypedVar = matchContext.getPattern().isTypedVar(tag.getNameElement());

    result = (isTypedVar || tag.getName().equals(tag2.getName()) );
    if (result && tag.getValueElement()!=null) {
      if (tag2.getValueElement()!=null) {
        result = match(tag.getValueElement(),tag2.getValueElement());
      }
      else {
        result = allowsAbsenceOfMatch(tag.getValueElement());
      }
    }

    if (result) {
      result = matchInAnyOrder(
        new DocValuesIterator(tag.getFirstChild()),
        new DocValuesIterator(tag2.getFirstChild())
      );

      if (result && isTypedVar) {
        result = handleTypedElement(tag.getNameElement(), tag2.getNameElement());
      }
    }
  }

  public void visitDocComment(final PsiDocComment comment) {
    PsiDocComment comment2;

    if (element instanceof PsiDocCommentOwner) {
      comment2 = ((PsiDocCommentOwner)element).getDocComment();

      if (comment2==null && comment.getTags()!=null) {
        // doc comment are not collapsed for inner classes!
        result = false;
        return;
      }
    } else {
      comment2 = (PsiDocComment) element;

      if (element.getParent() instanceof PsiDocCommentOwner) {
        result = false;
        return; // we should matched the doc before
      }
    }

    if (comment.getTags().length > 0) {
      result = matchInAnyOrder(comment.getTags(),comment2.getTags());
    } else {
      visitComment(comment);
    }
  }

  public void visitElement(PsiElement el) {
    result = el.textMatches(element);
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    final PsiArrayInitializerExpression expr2 = (PsiArrayInitializerExpression) element;

    result = matchSequentially(
      new ArrayBackedNodeIterator(expression.getInitializers()),
      new ArrayBackedNodeIterator(expr2.getInitializers())
    );
  }

  public void visitClassInitializer(PsiClassInitializer initializer) {
    PsiClassInitializer initializer2 = (PsiClassInitializer)element;
    result = match(initializer.getModifierList(),initializer2.getModifierList()) &&
             match(initializer.getBody(),initializer2.getBody());
  }

  public void visitCodeBlock(PsiCodeBlock block) {
    result = matchSons(block,element);
  }

  public void visitJavaToken(final PsiJavaToken token) {
    final PsiJavaToken anotherToken = (PsiJavaToken) element;

    result = token.getTokenType() == anotherToken.getTokenType() &&
             token.textMatches(anotherToken);
  }

  public static final String[] MODIFIERS = { PsiModifier.PUBLIC, PsiModifier.ABSTRACT, PsiModifier.FINAL, PsiModifier.STATIC,
    PsiModifier.PRIVATE,PsiModifier.PROTECTED, PsiModifier.ABSTRACT,
    PsiModifier.FINAL,PsiModifier.SYNCHRONIZED, PsiModifier.NATIVE,
    PsiModifier.VOLATILE, PsiModifier.STRICTFP
  };
  static { Arrays.sort(MODIFIERS); }

  public final void visitModifierList(final PsiModifierList list) {
    final PsiModifierList list2 = (PsiModifierList) element;

    for (String aMODIFIERS : MODIFIERS) {
      if (list.hasModifierProperty(aMODIFIERS) && !list2.hasModifierProperty(aMODIFIERS)) {
        result = false;
        return;
      }
    }

    final PsiAnnotation[] annotations = list.getAnnotations();
    if (annotations.length > 0) {
      HashSet<PsiAnnotation> set = new HashSet<PsiAnnotation>( Arrays.asList(annotations));
      
      for(PsiAnnotation annotation:annotations) {
        final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
        
        if (nameReferenceElement != null && MatchOptions.MODIFIER_ANNOTATION_NAME.equals(nameReferenceElement.getText())) {
          final PsiAnnotationParameterList parameterList = annotation.getParameterList();
          final PsiNameValuePair[] attributes = parameterList.getAttributes();
          
          for(PsiNameValuePair pair:attributes) {
            final PsiAnnotationMemberValue value = pair.getValue();
            if (value == null) continue;
            
            if (value instanceof PsiArrayInitializerMemberValue) {
              boolean matchedOne = false;
              
              for(PsiAnnotationMemberValue v:((PsiArrayInitializerMemberValue)value).getInitializers()) {
                final String name = StringUtil.stripQuotesAroundValue(v.getText());
                if (MatchOptions.INSTANCE_MODIFIER_NAME.equals(name)) {
                  if (list2.hasModifierProperty("static")) {
                    result = false;
                    return;
                  } else {
                    matchedOne = true;
                  }
                } else if (list2.hasModifierProperty(name)) {
                  matchedOne = true;
                  break;
                }
              }
              
              if (!matchedOne) {
                result = false;
                return;
              }
            } else {
              final String name = StringUtil.stripQuotesAroundValue(value.getText());
              if (MatchOptions.INSTANCE_MODIFIER_NAME.equals(name)) {
                if (list2.hasModifierProperty("static")) {
                  result = false;
                  return;
                }
              } else if (!list2.hasModifierProperty(name)) {
                result = false;
                return;
              }
            }
          }
          
          set.remove(annotation);
        }
      }
      
      result = set.size() == 0 || matchInAnyOrder(set.toArray(new PsiAnnotation[set.size()]),list2.getAnnotations());
    } else {
      result = true;
    }
  }

  private final boolean matchSonsOptionally(final PsiElement element,final PsiElement element2) {

    return (element == null && matchContext.getOptions().isLooseMatching()) ||
           matchSons(element,element2);
  }

  protected boolean matchInAnyOrder(final PsiReferenceList elements,final PsiReferenceList elements2) {
    if ( ( elements == null && matchContext.getOptions().isLooseMatching() ) ||
         elements == elements2 // null
       ) {
      return true;
    }

    return matchInAnyOrder(
      elements.getReferenceElements(),
      (elements2!=null)?elements2.getReferenceElements():PsiElement.EMPTY_ARRAY
    );
  }

  protected final boolean matchInAnyOrder(final PsiElement[] elements,final PsiElement[] elements2) {
    if (elements==elements2) return true;

    return matchInAnyOrder(
      new ArrayBackedNodeIterator(elements),
      new ArrayBackedNodeIterator(elements2)
    );
  }

  protected final boolean matchInAnyOrder(final NodeIterator elements,final NodeIterator elements2) {
    if ( ( !elements.hasNext() && matchContext.getOptions().isLooseMatching() ) ||
         (!elements.hasNext() && !elements2.hasNext())
       ) {
      return true;
    }

    return matchContext.getPattern().getHandler(elements.current()).matchInAnyOrder(
      elements,
      elements2,
      matchContext
    );
  }

  private final boolean compareClasses(final PsiClass clazz,final PsiClass clazz2) {
    PsiClass saveClazz = this.clazz;
    Handler.UnmatchedElementsListener listener = Handler.getUnmatchedElementsListener();

    this.clazz = clazz2;
    final PsiElement allRemainingClassContentElement = clazz.getUserData(CompiledPattern.ALL_CLASS_CONTENT_VAR_KEY);
    Handler.UnmatchedElementsListener mylistener = null;
    boolean result = false;

    if (allRemainingClassContentElement!=null) {
      Handler.setUnmatchedElementsListener(
        mylistener = new Handler.UnmatchedElementsListener() {
          List<PsiElement> l;

          public void matchedElements(List<PsiElement> elementList) {
            if (elementList!=null) {
              if (l==null) l = new LinkedList<PsiElement>(elementList);
              else l.addAll(elementList);
            }
          }

          public void commitUnmatched() {
            final SubstitutionHandler handler = (SubstitutionHandler) matchContext.getPattern().getHandler(allRemainingClassContentElement);

            for(PsiElement el = clazz2.getFirstChild();el!=null;el = el.getNextSibling()) {
              if (el instanceof PsiMember && (l==null || l.indexOf(el)==-1)) {
                handler.handle(el,matchContext);
              }
            }
          }
        }
      );
    }

    try {
      if (clazz.isInterface()!=clazz2.isInterface()) return false;

      if (!matchInAnyOrder(clazz.getExtendsList(),clazz2.getExtendsList())) {
        return false;
      }
      
      // check if implements is in extended classes implements
      final PsiReferenceList implementsList = clazz.getImplementsList();
      if (implementsList != null) {
        if (!matchInAnyOrder(implementsList,clazz2.getImplementsList())) {
          final PsiReferenceList anotherExtendsList = clazz2.getExtendsList();
          final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
          
          boolean accepted = false;
          
          if (referenceElements.length > 0 && anotherExtendsList != null) {
            final HierarchyNodeIterator iterator = new HierarchyNodeIterator(clazz2, true, true, false);
          
            accepted = matchInAnyOrder(new ArrayBackedNodeIterator(referenceElements),iterator);
          } 
          
          if (!accepted) return false;
        }
      }

      final PsiField[] fields  = clazz.getFields();

      if (fields.length > 0) {
        final PsiField[] fields2;
        fields2 = (matchContext.getPattern()).isRequestsSuperFields()?
                  clazz2.getAllFields():
                  clazz2.getFields();

        if (!matchInAnyOrder(fields,fields2)) {
          return false;
        }
      }

      final PsiMethod[] methods  = clazz.getMethods();

      if (methods.length > 0) {
        final PsiMethod[] methods2;
        methods2 = (matchContext.getPattern()).isRequestsSuperMethods()?
                   clazz2.getAllMethods():
                   clazz2.getMethods();

        if (!matchInAnyOrder(methods,methods2)) {
          return false;
        }
      }

      final PsiClass[] nestedClasses = clazz.getInnerClasses();

      if (nestedClasses.length > 0) {
        final PsiClass[] nestedClasses2 = (matchContext.getPattern()).isRequestsSuperInners()?
                                          clazz2.getAllInnerClasses():
                                          clazz2.getInnerClasses();

        if (!matchInAnyOrder(nestedClasses,nestedClasses2)) {
          return false;
        }
      }

      final PsiClassInitializer[] initializers = clazz.getInitializers();
      if (initializers.length > 0) {
        final PsiClassInitializer[] initializers2 = clazz2.getInitializers();

        if (!matchInAnyOrder(initializers,initializers2)) {
          return false;
        }
      }

      result = true;
      return result;
    } finally {
      if (result && mylistener!=null) mylistener.commitUnmatched();
      this.clazz = saveClazz;
      Handler.setUnmatchedElementsListener(listener);
    }
  }

  private boolean checkHierarchy(PsiElement element,PsiElement patternElement) {
    final Handler handler = matchContext.getPattern().getHandler(patternElement);
    if (handler instanceof SubstitutionHandler) {
      final SubstitutionHandler handler2 = (SubstitutionHandler)handler;

      if (!handler2.isSubtype()) {
        if (handler2.isStrictSubtype()) {
          // check if element is declared not in current class  (in ancestors)
          return (element.getParent()!=clazz);
        }
      } else {
        return true;
      }
    }

    // check if element is declared in current class (not in ancestors)
    return (element.getParent()==clazz);
  }

  public void visitAnnotation(PsiAnnotation annotation) {
    final PsiAnnotation psiAnnotation = (PsiAnnotation)element;

    result = match(annotation.getNameReferenceElement(),psiAnnotation.getNameReferenceElement()) &&
             matchInAnyOrder(annotation.getParameterList().getAttributes(),psiAnnotation.getParameterList().getAttributes());
  }

  public void visitNameValuePair(PsiNameValuePair pair) {
    final PsiIdentifier nameIdentifier = pair.getNameIdentifier();

    if (nameIdentifier!=null) {
      final Handler handler = matchContext.getPattern().getHandler(nameIdentifier);

      if (handler instanceof SubstitutionHandler) {
        result = ((SubstitutionHandler)handler).handle(((PsiNameValuePair)element).getNameIdentifier(),matchContext);
      } else {
        result = match(nameIdentifier, ((PsiNameValuePair)element).getNameIdentifier());
      }
    } else {
      result = true;
    }
  }

  public void visitField(PsiField psiField) {
    if (!checkHierarchy(element,psiField)) {
      result = false;
      return;
    }
    super.visitField(psiField);
  }

  private PsiClass clazz;

  public void visitAnonymousClass(final PsiAnonymousClass clazz) {
    final PsiAnonymousClass clazz2 = (PsiAnonymousClass) element;
    final boolean isTypedVar = matchContext.getPattern().isTypedVar(clazz.getFirstChild());

    result = (match(clazz.getBaseClassReference(),clazz2.getBaseClassReference()) || isTypedVar) &&
             matchSons(clazz.getArgumentList(),clazz2.getArgumentList()) &&
             compareClasses(clazz,clazz2);

    if (result && isTypedVar) {
      result = handleTypedElement(clazz.getFirstChild(),clazz2.getFirstChild());
    }
  }

  public void visitArrayAccessExpression(final PsiArrayAccessExpression slice) {
    final PsiArrayAccessExpression slice2 = (PsiArrayAccessExpression) element;

    result = match(slice.getArrayExpression(),slice2.getArrayExpression()) &&
             match(slice.getIndexExpression(),slice2.getIndexExpression());
  }

  public void visitReferenceExpression(final PsiReferenceExpression reference) {
    if (reference.getQualifier()==null) {
      final PsiElement nameElement = reference.getReferenceNameElement();

      if(nameElement != null && matchContext.getPattern().isTypedVar(nameElement)) {
        if (element instanceof PsiReferenceExpression) {
          final PsiReferenceExpression psiReferenceExpression = ((PsiReferenceExpression)element);

          if (psiReferenceExpression.getQualifierExpression()==null) {
            element = psiReferenceExpression.getReferenceNameElement();
          }
        }
        result = handleTypedElement(nameElement, element);
        return;
      }
    }

    if (!(element instanceof PsiReferenceExpression)) {
      result = false;
      return;
    }

    final PsiReferenceExpression reference2 = (PsiReferenceExpression) element;

    // just variable
    if (reference.getQualifier()==null &&
        reference2.getQualifier() == null
       ) {
      result = reference.getReferenceNameElement().textMatches(reference2.getReferenceNameElement());
      return;
    }

    // handle field selection
    if ( !(element.getParent() instanceof PsiMethodCallExpression) && // element is not a method) &&
         reference.getQualifierExpression()!=null &&
         ( reference2.getQualifierExpression()!=null ||
           ( reference.getQualifierExpression() instanceof PsiThisExpression &&
             reference2.getQualifierExpression() == null &&
             MatchUtils.getReferencedElement(element) instanceof PsiField
           )
         )
       ) {
      if (matchContext.getPattern().isTypedVar(reference.getReferenceNameElement())) {
        result = handleTypedElement(reference.getReferenceNameElement(), reference2.getReferenceNameElement());
      } else {
        result = reference.getReferenceNameElement().textMatches(reference2.getReferenceNameElement());
      }

      // @todo fixme here
      if (result &&
          reference.getQualifierExpression()!=null &&
          reference2.getQualifierExpression()!=null
         ) {
        result = match(reference.getQualifierExpression(), reference2.getQualifierExpression());
      }

      return;
    }

    result = false;
  }

  private final boolean compareBody(final PsiElement el1,final PsiElement el2) {
    PsiElement compareElemement1 = el1;
    PsiElement compareElemement2 = el2;

    if (matchContext.getOptions().isLooseMatching()) {
      if (el1 instanceof PsiBlockStatement ) {
        compareElemement1 = ((PsiBlockStatement)el1).getCodeBlock().getFirstChild();
      }

      if (el2 instanceof PsiBlockStatement ) {
        compareElemement2 = ((PsiBlockStatement)el2).getCodeBlock().getFirstChild();
      }
    }

    return matchSequentially( compareElemement1, compareElemement2 );
  }

  public void visitConditionalExpression(final PsiConditionalExpression cond) {
    final PsiConditionalExpression cond2 = (PsiConditionalExpression) element;

    result = match(cond.getCondition(),cond2.getCondition()) &&
             matchSons(cond,cond2);
  }

  public void visitBinaryExpression(final PsiBinaryExpression binExpr) {
    final PsiBinaryExpression binExpr2 = (PsiBinaryExpression) element;

    result = binExpr.getOperationSign().textMatches(binExpr2.getOperationSign()) &&
             match(binExpr.getLOperand(), binExpr2.getLOperand()) &&
             match(binExpr.getROperand(),binExpr2.getROperand());
  }

  protected final boolean handleTypedElement(final PsiElement typedElement, final PsiElement match) {
    final SubstitutionHandler handler = (SubstitutionHandler) matchContext.getPattern().getHandler(typedElement);
    return handler.handle(match,matchContext);
  }

  public void visitVariable(final PsiVariable var) {
    boolean isTypedVar = matchContext.getPattern().isTypedVar(var.getNameIdentifier());
    boolean isTypedInitializer = var.getInitializer() != null &&
                                 matchContext.getPattern().isTypedVar(var.getInitializer()) &&
                                 var.getInitializer() instanceof PsiReferenceExpression;
    final PsiVariable var2 = (PsiVariable) element;

    result = (var.getName().equals(var2.getName()) || isTypedVar) &&
             ( ( var.getParent() instanceof PsiClass && ((PsiClass)var.getParent()).isInterface()) ||
               match(var.getModifierList(),var2.getModifierList())
             ) &&
             match(var.getTypeElement(),var2.getTypeElement());

    if (result) {
      // Check initializer
      final PsiExpression var2Initializer = var2.getInitializer();

      result = match(var.getInitializer(), var2Initializer) ||
               ( isTypedInitializer &&
                 var2Initializer == null &&
                 allowsAbsenceOfMatch(var.getInitializer())
               );
    }

    if (result && isTypedVar) {
      result = handleTypedElement(var.getNameIdentifier(),var2.getNameIdentifier());
    }
  }

  public void visitMethodCallExpression(final PsiMethodCallExpression mcall) {
    final PsiMethodCallExpression mcall2 = (PsiMethodCallExpression) element;
    final PsiReferenceExpression mcallRef1 = mcall.getMethodExpression();
    final PsiReferenceExpression mcallRef2 = mcall2.getMethodExpression();

    final String mcallname1 = mcallRef1.getReferenceName();
    final String mcallname2 = mcallRef2.getReferenceName();
    boolean isTypedVar = matchContext.getPattern().isTypedVar(mcallRef1.getReferenceNameElement());

    if (!mcallname1.equals(mcallname2) && !isTypedVar) {
      result = false;
      return;
    }

    if (mcallRef1.getQualifierExpression()!=null) {
      String FQN = mcall.getUserData(CompiledPattern.FQN);
      boolean processedFQN =  false;

      if (FQN!=null) {
        PsiElement element = mcallRef2.getQualifierExpression();
        if (element instanceof PsiJavaReference) {
          PsiElement clazz = ((PsiJavaReference)element).resolve();

          if (clazz instanceof PsiClass) {
            result = ((PsiClass)clazz).getQualifiedName().equals(FQN);
            if (!result) return;
            processedFQN = true;
          }
        }
      }

      if (!processedFQN) {
        // @todo this branching could be even better
        if (mcallRef2.getQualifierExpression()!=null) {
          result = match(
            mcallRef1.getQualifierExpression(),
            mcallRef2.getQualifierExpression()
          );
          if (!result) return;
        } else {
          Handler handler = matchContext.getPattern().getHandler(mcallRef1.getQualifierExpression());
          if (!(handler instanceof SubstitutionHandler) ||
              ((SubstitutionHandler)handler).getMinOccurs()!=0) {
            result = false;
            return;
          } else {
            // we may have not ? expr_type constraint set on qualifier expression so validate it
            SubstitutionHandler substitutionHandler = (SubstitutionHandler)handler;

            if (substitutionHandler.getPredicate()!=null) {
              boolean isnot = false;
              Handler _predicate = substitutionHandler.getPredicate();
              ExprTypePredicate predicate = null;

              if (_predicate instanceof NotPredicate) {
                isnot = true;
                _predicate = ((NotPredicate)_predicate).getHandler();
              }

              if (_predicate instanceof ExprTypePredicate) {
                predicate = (ExprTypePredicate)_predicate;
              }

              if (predicate != null) {
                PsiMethod method = (PsiMethod)mcallRef2.resolve();
                if (method != null) {
                  result = predicate.checkClass((PsiClass)method.getParent(),matchContext);
                  if (isnot) result = !result;
                } else {
                  result = false;
                }

                if (!result) return;
              }
            }
          }
        }
      }
    } else if (mcallRef2.getQualifierExpression()!=null) {
      result = false;
      return;
    }

    result = matchSons(mcall.getArgumentList(),mcall2.getArgumentList());

    if (result && isTypedVar) {
      result &= handleTypedElement(mcallRef1.getReferenceNameElement(),mcallRef2.getReferenceNameElement());
    }
  }

  public void visitExpressionStatement(final PsiExpressionStatement expr) {
    final PsiExpressionStatement expr2 = (PsiExpressionStatement) element;

    result = match(expr.getExpression(),expr2.getExpression());
  }

  public void visitLiteralExpression(final PsiLiteralExpression const1) {
    final PsiLiteralExpression const2 = (PsiLiteralExpression) element;

    Handler handler = (Handler)const1.getUserData(CompiledPattern.HANDLER_KEY);

    if (handler instanceof SubstitutionHandler) {
      int offset = 0;
      int length = const2.getTextLength();
      final String text = const2.getText();

      if (length > 2 && text.charAt(0) == '"' && text.charAt(length-1)=='"') {
        length--;
        offset++;
      }
      result = ((SubstitutionHandler)handler).handle(const2,offset,length,matchContext);
    } else if (handler!=null) {
      result = handler.match(const1,const2,matchContext);
    } else {
      result = const1.textMatches(const2);
    }
  }

  public void visitAssignmentExpression(final PsiAssignmentExpression assign) {
    final PsiAssignmentExpression assign2 = (PsiAssignmentExpression)element;

    result =
      assign.getOperationSign().textMatches(assign2.getOperationSign()) &&
      match(assign.getLExpression(),assign2.getLExpression()) &&
      match(assign.getRExpression(),assign2.getRExpression());
  }

  public void visitIfStatement(final PsiIfStatement if1) {
    final PsiIfStatement if2 = (PsiIfStatement) element;

    result = match(if1.getCondition(),if2.getCondition()) &&
             compareBody(if1.getThenBranch(),if2.getThenBranch()) &&
             compareBody(if1.getElseBranch(),if2.getElseBranch());
  }

  public void visitSwitchStatement(final PsiSwitchStatement switch1) {
    final PsiSwitchStatement switch2 = (PsiSwitchStatement) element;

    result = match(switch1.getExpression(),switch2.getExpression()) &&
             matchSons(switch1.getBody(),switch2.getBody());
  }

  public void visitForStatement(final PsiForStatement for1) {
    final PsiForStatement for2 = (PsiForStatement) element;

    final PsiStatement initialization = for1.getInitialization();
    Handler handler = matchContext.getPattern().getHandler(initialization);

    result = handler.match(initialization, for2.getInitialization(), matchContext) &&
             match(for1.getCondition(),for2.getCondition()) &&
             match(for1.getUpdate(),for2.getUpdate()) &&
             compareBody(for1.getBody(),for2.getBody());
  }

  public void visitForeachStatement(PsiForeachStatement for1) {
    final PsiForeachStatement for2 = (PsiForeachStatement) element;

    result = match(for1.getIterationParameter(),for2.getIterationParameter()) &&
             match(for1.getIteratedValue(),for2.getIteratedValue()) &&
             compareBody(for1.getBody(),for2.getBody());
  }

  public void visitWhileStatement(final PsiWhileStatement while1) {
    final PsiWhileStatement while2 = (PsiWhileStatement)element;

    result = match(while1.getCondition(),while2.getCondition()) &&
             compareBody(while1.getBody(),while2.getBody());
  }

  public void visitBlockStatement(final PsiBlockStatement block) {
    if (element instanceof PsiCodeBlock &&
        !(element.getParent() instanceof PsiBlockStatement)
       ) {
      result = matchSons(block.getCodeBlock(),element);
    } else {
      final PsiBlockStatement block2 = (PsiBlockStatement) element;
      result = matchSons(block,block2);
    }
  }

  public void visitDeclarationStatement(final PsiDeclarationStatement dcl) {
    final PsiDeclarationStatement declaration = (PsiDeclarationStatement)element;
    result = matchInAnyOrder(dcl.getDeclaredElements(), declaration.getDeclaredElements());
  }

  public void visitDoWhileStatement(final PsiDoWhileStatement while1) {
    final PsiDoWhileStatement while2 = (PsiDoWhileStatement) element;

    result = match(while1.getCondition(),while2.getCondition()) &&
             compareBody(while1.getBody(),while2.getBody());
  }

  public void visitReturnStatement(final PsiReturnStatement return1) {
    final PsiReturnStatement return2 = (PsiReturnStatement) element;

    result = match(return1.getReturnValue(),return2.getReturnValue());
  }

  public void visitPostfixExpression(final PsiPostfixExpression postfix) {
    final PsiPostfixExpression postfix2 = (PsiPostfixExpression)element;

    result = postfix.getOperationSign().textMatches(postfix2.getOperationSign())
             && match(postfix.getOperand(), postfix2.getOperand());
  }

  public void visitPrefixExpression(final PsiPrefixExpression prefix) {
    final PsiPrefixExpression prefix2 = (PsiPrefixExpression)element;

    result = prefix.getOperationSign().textMatches(prefix2.getOperationSign())
             && match(prefix.getOperand(), prefix2.getOperand());
  }

  public void visitAssertStatement(final PsiAssertStatement assert1) {
    final PsiAssertStatement assert2 = (PsiAssertStatement) element;

    result = match(assert1.getAssertCondition(),assert2.getAssertCondition()) &&
             match(assert1.getAssertDescription(),assert2.getAssertDescription());
  }

  public void visitBreakStatement(final PsiBreakStatement break1) {
    final PsiBreakStatement break2 = (PsiBreakStatement) element;

    result = match(break1.getLabelIdentifier(),break2.getLabelIdentifier());
  }

  public void visitContinueStatement(final PsiContinueStatement continue1) {
    final PsiContinueStatement continue2 = (PsiContinueStatement) element;

    result = match(continue1.getLabelIdentifier(),continue2.getLabelIdentifier());
  }

  public void visitSuperExpression(final PsiSuperExpression super1) {
    result = true;
  }

  public void visitThisExpression(final PsiThisExpression this1) {
    result = element instanceof PsiThisExpression;
  }

  public void visitSynchronizedStatement(final PsiSynchronizedStatement synchronized1) {
    final PsiSynchronizedStatement synchronized2 = (PsiSynchronizedStatement) element;

    result = match(synchronized1.getLockExpression(),synchronized2.getLockExpression()) &&
             matchSons(synchronized1.getBody(),synchronized2.getBody());
  }

  public void visitThrowStatement(final PsiThrowStatement throw1) {
    final PsiThrowStatement throw2 = (PsiThrowStatement) element;

    result = match(throw1.getException(),throw2.getException());
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expr) {
    if (element instanceof PsiParenthesizedExpression) {
      result = matchSons(expr,element);
    } else {
      result = false;
    }
  }

  public void visitTryStatement(final PsiTryStatement try1) {
    final PsiTryStatement try2 = (PsiTryStatement) element;

    result = matchSons(try1.getTryBlock(),try2.getTryBlock());

    if (!result) return;

    final PsiCodeBlock[] catches1 = try1.getCatchBlocks();
    final PsiParameter[] catchesArgs1 = try1.getCatchBlockParameters();
    final PsiCodeBlock finally1 = try1.getFinallyBlock();

    final PsiCodeBlock[] catches2 = try2.getCatchBlocks();
    final PsiParameter[] catchesArgs2 = try2.getCatchBlockParameters();
    final PsiCodeBlock finally2 = try2.getFinallyBlock();

    if (!matchContext.getOptions().isLooseMatching() &&
        ( ( catches1.length == 0 &&
            catches2.length!=0
          ) ||
            ( finally1 == null &&
              finally2 != null
            )
        )
       ) {
      result = false;
    } else {
      List<PsiCodeBlock> unmatchedCatchBlocks = new ArrayList<PsiCodeBlock>();
      List<PsiParameter> unmatchedCatchParams = new ArrayList<PsiParameter>();

      for(int j = 0;j < catches2.length;++j) {
        unmatchedCatchBlocks.add(catches2[j]);
        unmatchedCatchParams.add(catchesArgs2[j]);
      }

      for(int i = 0, j; i < catches1.length; ++i) {
        for(j = 0; j < unmatchedCatchBlocks.size(); ++j) {

          if (i < catchesArgs1.length &&
              match(catchesArgs1[i],unmatchedCatchParams.get(j)) &&
              match(catches1[i],unmatchedCatchBlocks.get(j))
             ) {
            unmatchedCatchBlocks.remove(j);
            unmatchedCatchParams.remove(j);
            break;
          }
        }

        if (j==catches2.length) {
          result = false;
          return;
        }
      }

      if (finally1!=null) {
        result = matchSons(finally1,finally2);
      }

      if (result && unmatchedCatchBlocks.size() > 0 && !matchContext.getOptions().isLooseMatching()) {
        try2.putUserData(MatcherImplUtil.UNMATCHED_CATCH_BLOCK_CONTENT_VAR_KEY,unmatchedCatchBlocks);
        try2.putUserData(MatcherImplUtil.UNMATCHED_CATCH_PARAM_CONTENT_VAR_KEY,unmatchedCatchParams);
      }
    }
  }

  public void visitSwitchLabelStatement(final PsiSwitchLabelStatement case1) {
    final PsiSwitchLabelStatement case2 = (PsiSwitchLabelStatement) element;

    result = case1.isDefaultCase() == case2.isDefaultCase() &&
             match(case1.getCaseValue(),case2.getCaseValue());
  }

  /**
   * Identifies the match between given element of program tree and pattern element
   * @param el1 the pattern for matching
   * @param el2 the tree element for matching
   * @return true if equal and false otherwise
   */
  public boolean match(final PsiElement el1,final PsiElement el2) {
    // null
    if (el1==el2) return true;
    if (el2==null || el1==null) {
      // this a bug!
      return false;
    }

    // copy changed data to local stack
    PsiElement prevElement = element;
    element = el2;

    try {
      el1.accept(this);
    } catch(ClassCastException ex) {
      result = false;
    } catch(RuntimeException ex) {
      if(lastPatternElement==null) {
        lastPatternElement = el1;
        lastMatchedElement = el2;
      }
      throw ex;
    } finally {
      element = prevElement;
    }

    return result;
  }

  // Matches the sons of given elements to find equality
  // @param el1 the pattern element for matching
  // @param el2 the tree element for matching
  // @return if they are equal and false otherwise
  protected boolean matchSons(final PsiElement el1,final PsiElement el2) {
    if (el1==null || el2==null) return el1 == el2;
    return matchSequentially(el1.getFirstChild(),el2.getFirstChild());
  }

  public boolean shouldAdvanceThePattern(final PsiElement element, PsiElement match) {
    Handler handler = matchContext.getPattern().getHandler(element);

    return handler.shouldAdvanceThePatternFor(element, match);
  }

  public boolean allowsAbsenceOfMatch(final PsiElement element) {
    Handler handler = matchContext.getPattern().getHandler(element);

    if (handler instanceof SubstitutionHandler &&
        ((SubstitutionHandler)handler).getMinOccurs() == 0) {
      return true;
    }
    return false;
  }

  // Matches tree segments starting with given elements to find equality
  // @param el1 the pattern element for matching
  // @param el2 the tree element for matching
  // @return if they are equal and false otherwise
  private boolean matchSequentially(NodeIterator nodes,NodeIterator nodes2) {
    return continueMatchingSequentially(nodes, nodes2,matchContext);
  }

  public static boolean continueMatchingSequentially(final NodeIterator nodes, final NodeIterator nodes2, MatchContext matchContext) {
    if (!nodes.hasNext()) {
      return nodes.hasNext() == nodes2.hasNext();
    }

    return matchContext.getPattern().getHandler(nodes.current()).matchSequentially(
      nodes,
      nodes2,
      matchContext
    );
  }

  // Matches tree segments starting with given elements to find equality
  // @param el1 the pattern element for matching
  // @param el2 the tree element for matching
  // @return if they are equal and false otherwise
  protected boolean matchSequentially(PsiElement el1,PsiElement el2) {
    //if (el1==null || el2==null) return el1 == el2;
    return matchSequentially( new FilteringNodeIterator(el1), new FilteringNodeIterator(el2) );
  }

  private PsiElement lastPatternElement;
  private PsiElement lastMatchedElement;

  String getLastPatternElementText() {
    return (lastPatternElement!=null)?lastPatternElement.getText():null;
  }

  String getLastMatchedElementText() {
    return (lastMatchedElement!=null)?lastMatchedElement.getText():null;
  }

  MatchResultImpl processOneMatch(MatchResultImpl realResult) {
    MatchResultImpl result = realResult;
    MatchResultImpl candidateResult;
    Iterator candidateSons;

    if (realResult.hasSons() &&
        (candidateResult = (MatchResultImpl)(candidateSons = realResult.getSons()).next()) != null &&
        candidateResult.getName().equals(realResult.getName())
       ) {
      // many results of one, show them nicely
      result = candidateResult;
      while(candidateSons.hasNext()) {
        matchContext.getSink().newMatch(result);
        result = (MatchResultImpl) candidateSons.next();
      }
    }

    return result;
  }

  /**
   * Descents the tree in depth finding matches
   * @param elements the element for which the sons are looked for match
   */
  void matchContext(final NodeIterator elements) {
    final CompiledPattern pattern = matchContext.getPattern();
    final NodeIterator patternNodes = pattern.getNodes().clone();

    if (!patternNodes.hasNext()) return;

    List<PsiElement> matchedNodes = null;
    PsiElement patternElement;
    final MatchResultImpl saveResult = matchContext.hasResult() ? matchContext.getResult():null;
    matchContext.setResult(null);

    Loop:
    for(;elements.hasNext();elements.advance()) {
      final PsiElement element = elements.current();

      patternElement = patternNodes.current();

      final Handler handler = matchContext.getPattern().getHandler(patternElement);
      if (!handler.match(patternElement,element,matchContext) &&
          !allowsAbsenceOfMatch(patternElement)) {
        if (matchContext.hasResult())
          matchContext.clearResult();

        if (matchedNodes!=null && matchedNodes.size()>0) {
          patternNodes.reset();
          elements.rewind(matchedNodes.size());
        }

        if (matchContext.getPattern().getStrategy().continueMatching(element)) {
          matchContext(
            new FilteringNodeIterator(
              new ArrayBackedNodeIterator(element.getChildren())
            )
          );
        }

        if (matchedNodes!=null) matchedNodes.clear();
        continue;
      }

      if (matchedNodes == null) matchedNodes = new LinkedList<PsiElement>();

      PsiElement elementToAdd = element;

      if (patternElement instanceof PsiComment &&
          element instanceof PsiMember
         ) {
        // psicomment and psidoccomment are placed inside the psimember next to them so
        // simple topdown matching should do additional "dances" to cover this case.
        elementToAdd = element.getFirstChild();
        assert elementToAdd instanceof PsiComment;
      }

      matchedNodes.add(elementToAdd);

      if (handler.shouldAdvanceThePatternFor(patternElement, element)) {
        patternNodes.advance();
      }

      if (!handler.shouldAdvanceTheMatchFor(patternElement, element)) {
        elements.rewind();
      }

      if (!patternNodes.hasNext()) {
        // match found
        MatchResultImpl result = matchContext.getResult();

        final Iterator sons = result.getSons();

        // There is no substitutions so show the context
        if (!sons.hasNext() || matchContext.getOptions().isResultIsContextMatch()) {
          processNoSubstitutionMatch(matchedNodes, result);
        } else {
          boolean seenSearchTarget = false;

          while(sons.hasNext()) {
            MatchResultImpl realResult = (MatchResultImpl) sons.next();

            if (realResult.isTarget()) {
              if (seenSearchTarget) matchContext.getSink().newMatch(result);
              result = processOneMatch(realResult);
              seenSearchTarget = true;
            }
          }

          if (!seenSearchTarget) {
            processNoSubstitutionMatch(matchedNodes, result);
          }
        }

        matchContext.getSink().newMatch(result);

        patternNodes.reset();
        matchedNodes.clear();
        matchContext.setResult(null);
      }

      // try to find the pattern in descendants
      if (matchContext.getOptions().isRecursiveSearch() &&
          matchContext.getPattern().getStrategy().continueMatching(element)) {
        matchContext(
          new FilteringNodeIterator(
            new ArrayBackedNodeIterator(element.getChildren())
          )
        );
      }
    }
    matchContext.setResult(saveResult);
  }

  private static void processNoSubstitutionMatch(List<PsiElement> matchedNodes, MatchResultImpl result) {
    boolean complexMatch = matchedNodes.size() > 1;
    final PsiElement match = matchedNodes.get(0);

    if (!complexMatch) {
      result.setMatchRef(new SmartPsiPointer(match));
      result.setMatchImage(match.getText());
    } else {
      MatchResultImpl sonresult;

      for (final PsiElement matchStatement : matchedNodes) {
        result.getMatches().add(
          sonresult = new MatchResultImpl(
            MatchResult.LINE_MATCH,
            matchStatement.getText(),
            new SmartPsiPointer(matchStatement),
            true
          )
        );

        sonresult.setParent(result);
      }

      result.setMatchRef(
        new SmartPsiPointer(match)
      );
      result.setMatchImage(
        match.getText()
      );
      result.setName(MatchResult.MULTI_LINE_MATCH);
    }
  }

  void setMatchContext(MatchContext matchContext) {
    this.matchContext = matchContext;
  }

  public void visitInstanceOfExpression(final PsiInstanceOfExpression instanceOf) {
    final PsiInstanceOfExpression instanceOf2 = (PsiInstanceOfExpression) element;
    result = match(instanceOf.getOperand(),instanceOf2.getOperand()) &&
             matchType(instanceOf.getCheckType(),instanceOf2.getCheckType());
  }

  protected boolean matchType(final PsiElement _type, final PsiElement _type2) {
    boolean result;
    PsiElement el = _type;
    PsiElement el2 = _type2;
    PsiType type1 = null;
    PsiType type2 = null;

    // check for generics
    if (_type instanceof PsiTypeElement &&
        ((PsiTypeElement)_type).getInnermostComponentReferenceElement()!=null
       ) {
      el = ((PsiTypeElement)_type).getInnermostComponentReferenceElement();
      type1 = ((PsiTypeElement)_type).getType();
      PsiReferenceParameterList list = ((PsiJavaCodeReferenceElement)el).getParameterList();
      PsiElement[] typeparams = null;

      if (_type2 instanceof PsiTypeElement &&
          ((PsiTypeElement)_type2).getInnermostComponentReferenceElement()!=null
         ) {
        el2 = ((PsiTypeElement)_type2).getInnermostComponentReferenceElement();
        type2 = ((PsiTypeElement)_type2).getType();
      }

      if (el2 instanceof PsiJavaCodeReferenceElement) {
        typeparams = ((PsiJavaCodeReferenceElement)el2).getParameterList().getTypeParameterElements();
        if (typeparams.length > 0) {
          el2 = ((PsiJavaCodeReferenceElement)el2).getReferenceNameElement();
        }
      }
      else if (el2 instanceof PsiTypeParameter) {
        el2 = ((PsiTypeParameter)el2).getNameIdentifier();
      } else if (el2 instanceof PsiClass &&
                 ((PsiClass)el2).getTypeParameters().length > 0
              ) {
        typeparams = ((PsiClass)el2).getTypeParameters();
        el2 = ((PsiClass)el2).getNameIdentifier();
      } else if (el2 instanceof PsiMethod &&
                 ((PsiMethod)el2).getTypeParameters().length > 0
              ) {
        typeparams = ((PsiMethod)_type2).getTypeParameters();
        el2 = ((PsiMethod)_type2).getNameIdentifier();
      }

      if (list!=null && list.getTypeParameterElements().length>0) {
        result = typeparams!=null &&
                 matchInAnyOrder(
                   list.getTypeParameterElements(),
                   typeparams
                 );

        if (!result) return false;
        el = ((PsiJavaCodeReferenceElement)el).getReferenceNameElement();
      } else {
        if (_type2 instanceof PsiTypeElement) {
          type2 = ((PsiTypeElement)_type2).getType();

          if (typeparams == null || typeparams.length == 0) {
            final PsiJavaCodeReferenceElement innermostComponentReferenceElement = ((PsiTypeElement)_type2).getInnermostComponentReferenceElement();
            if (innermostComponentReferenceElement != null) el2 = innermostComponentReferenceElement;
          } else {
            el2 = _type2;
          }
        }
      }
    }

    final int array2Dims = (type2 != null ? type2.getArrayDimensions():0) + countCStyleArrayDeclarationDims(_type2);
    final int arrayDims = ( type1 != null ? type1.getArrayDimensions():0) + countCStyleArrayDeclarationDims(_type);

    if (matchContext.getPattern().isTypedVar(el)) {
      final SubstitutionHandler handler = (SubstitutionHandler) matchContext.getPattern().getHandler(el);

      RegExpPredicate regExpPredicate = null;

      if (arrayDims != 0) {
        if (arrayDims != array2Dims) {
          return false;
        }
      } else if (array2Dims != 0) {
        regExpPredicate = Handler.getSimpleRegExpPredicate(handler);

        if (regExpPredicate != null) {
          final PsiType type = type2;
          regExpPredicate.setNodeTextGenerator(new RegExpPredicate.NodeTextGenerator() {
            public String getText(PsiElement element) {
              StringBuilder builder = new StringBuilder(RegExpPredicate.getMeaningfulText(element));
              for(int i = 0; i < array2Dims; ++i) builder.append("[]");
              return builder.toString();
            }
          });
        }
      }

      try {
        if (handler.isSubtype() || handler.isStrictSubtype()) {
          // is type2 is (strict) subtype of type
          final NodeIterator node = new HierarchyNodeIterator(el2, true, true);

          if (handler.isStrictSubtype()) {
            node.advance();
          }

          while (node.hasNext() && !handler.validate(node.current(), 0, -1, matchContext)) {
            node.advance();
          }

          if (node.hasNext()) {
            handler.addResult(el2, 0, -1, matchContext);
            return true;
          } else {
            return false;
          }
        } else {
          return handler.handle(el2, matchContext);
        }
      }
      finally {
        if (regExpPredicate != null) regExpPredicate.setNodeTextGenerator( null );
      }
    }

    if (array2Dims != arrayDims) {
      return false;
    }

    final String text = el.getText();
    if (text.indexOf('.')==-1 || !(el2 instanceof PsiJavaReference)) {
      return MatchUtils.compareWithNoDifferenceToPackage(text,el2.getText());
    } else {
      PsiElement element2 = ((PsiJavaReference)el2).resolve();

      if (element2!=null) {
        return text.equals(((PsiClass)element2).getQualifiedName());
      } else {
        return MatchUtils.compareWithNoDifferenceToPackage(text,el2.getText());
      }
    }
  }

  private static int countCStyleArrayDeclarationDims(final PsiElement type2) {
    if (type2 != null) {
      final PsiElement parentElement = type2.getParent();

      if (parentElement instanceof PsiVariable) {
        final PsiIdentifier psiIdentifier = ((PsiVariable)parentElement).getNameIdentifier();
        if (psiIdentifier == null) return 0;

        int count = 0;
        for(PsiElement sibling = psiIdentifier.getNextSibling();sibling != null; sibling = sibling.getNextSibling()) {
          if (sibling instanceof PsiJavaToken) {
            final IElementType tokenType = ((PsiJavaToken)sibling).getTokenType();
            if (tokenType == JavaTokenType.LBRACKET) ++count;
            if (tokenType != JavaTokenType.RBRACKET) break;
          }
        }

        return count;
      }
    }
    return 0;
  }

  public void visitNewExpression(final PsiNewExpression new1) {
    if (element instanceof PsiArrayInitializerExpression &&
        element.getParent() instanceof PsiVariable &&
        new1.getArrayDimensions().length == 0 &&
        new1.getArrayInitializer() != null
       ) {
      result = match(new1.getClassReference(),((PsiVariable)element.getParent()).getTypeElement());
        matchSons(new1.getArrayInitializer(),element);
      return;
    }

    final PsiNewExpression new2 = (PsiNewExpression) element;

    if (new1.getClassReference() != null) {
      if (new2.getClassReference() != null) {
        result = match(new1.getClassReference(),new2.getClassReference()) &&
                 matchSons(new1.getArrayInitializer(),new2.getArrayInitializer());

        if (result) {
          // matching dims
          matchArrayDims(new1, new2);
        }
        return;
      } else {
        // match array of primitive by new 'T();
        final PsiKeyword newKeyword = PsiTreeUtil.getChildOfType(new2, PsiKeyword.class);
        final PsiElement element = PsiTreeUtil.getNextSiblingOfType(newKeyword, PsiWhiteSpace.class);

        if (element != null && element.getNextSibling() instanceof PsiKeyword) {
          ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(true);

          result = match(new1.getClassReference(),element.getNextSibling()) &&
                   matchSons(new1.getArrayInitializer(),new2.getArrayInitializer());

          ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(false);
          if (result) {
            // matching dims
            matchArrayDims(new1, new2);
          }

          return;
        }
      }
    }

    if (new1.getClassReference() == new2.getClassReference()) {
      // probably anonymous class or array of primitive type
      ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(true);
      result = matchSons(new1,new2);
      ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(false);
    } else if (new1.getAnonymousClass()==null &&
               new1.getClassReference()!=null &&
               new2.getAnonymousClass()!=null) {
      // allow matching anonymous class without pattern
      result = match(new1.getClassReference(),new2.getAnonymousClass().getBaseClassReference()) &&
               matchSons(new1.getArgumentList(),new2.getArgumentList());
    } else {
      result = false;
    }
  }

  private void matchArrayDims(final PsiNewExpression new1, final PsiNewExpression new2) {
    final PsiExpression[] arrayDims = new1.getArrayDimensions();
    final PsiExpression[] arrayDims2 = new2.getArrayDimensions();

    if (arrayDims!=null && arrayDims2!=null && arrayDims.length == arrayDims2.length && arrayDims.length != 0) {
      for(int i = 0; i < arrayDims.length; ++i) {
        result = match(arrayDims[i],arrayDims2[i]);
        if (!result) return;
      }
    } else {
      result = (arrayDims == arrayDims2) && matchSons(new1.getArgumentList(),new2.getArgumentList());
    }
  }

  public void visitKeyword(PsiKeyword keyword) {
    result = keyword.textMatches(element);
  }

  public void visitTypeCastExpression(final PsiTypeCastExpression cast) {
    final PsiTypeCastExpression cast2 = (PsiTypeCastExpression) element;

    result = ( match(cast.getCastType(),cast2.getCastType()) ) &&
             match(cast.getOperand(),cast2.getOperand());
  }

  public void visitClassObjectAccessExpression(final PsiClassObjectAccessExpression expr) {
    final PsiClassObjectAccessExpression expr2 = (PsiClassObjectAccessExpression) element;

    result = match(expr.getOperand(),expr2.getOperand());
  }

  public void visitReferenceElement(final PsiJavaCodeReferenceElement ref) {
    result = matchType(ref,element);
  }

  public void visitTypeElement(final PsiTypeElement typeElement) {
    result = matchType(typeElement,element);
  }

  public void visitTypeParameter(PsiTypeParameter psiTypeParameter) {
    final PsiTypeParameter parameter = (PsiTypeParameter) element;
    final PsiElement[] children = psiTypeParameter.getChildren();
    final PsiElement[] children2 = parameter.getChildren();

    final Handler handler = matchContext.getPattern().getHandler(children[0]);

    if (handler instanceof SubstitutionHandler) {
      result = ((SubstitutionHandler)handler).handle(children2[0],matchContext);
    } else {
      result = children[0].textMatches(children2[0]);
    }

    if (result && children.length > 2) {
      // constraint present
      if (children2.length == 2) {
        result = false;
        return;
      }

      if (!children[2].getFirstChild().textMatches(children2[2].getFirstChild())) {
        // constraint type (extends)
        result = false;
        return;
      }
      result = matchInAnyOrder(children[2].getChildren(), children2[2].getChildren());
    }
  }

  public void visitClass(PsiClass clazz) {
    if (clazz.getTypeParameters().length > 0) {
      result = match(
        clazz.getTypeParameterList(),
        ((PsiClass)element).getTypeParameterList()
      );

      if (!result) return;
    }

    PsiClass clazz2;

    if (element instanceof PsiDeclarationStatement &&
        element.getFirstChild() instanceof PsiClass
       ) {
      clazz2 = (PsiClass) element.getFirstChild();
    } else {
      clazz2 = (PsiClass) element;
    }

    final boolean isTypedVar = matchContext.getPattern().isTypedVar(clazz.getNameIdentifier());

    if (clazz.getModifierList().getTextLength() > 0) {
      if (!match(clazz.getModifierList(),clazz2.getModifierList())) {
        result = false;
        return;
      }
    }

    result = (clazz.getName().equals(clazz2.getName()) || isTypedVar) &&
             compareClasses(clazz,clazz2);

    if (result && isTypedVar) {
      PsiElement id = clazz2.getNameIdentifier();
      if (id==null) id = clazz2;
      result = handleTypedElement(clazz.getNameIdentifier(),id);
    }
  }

  public void visitTypeParameterList(PsiTypeParameterList psiTypeParameterList) {
    result = matchSequentially(
      psiTypeParameterList.getFirstChild(),
      element.getFirstChild()
    );
  }

  public void visitMethod(PsiMethod method) {
    final boolean isTypedVar = matchContext.getPattern().isTypedVar(method.getNameIdentifier());
    final PsiMethod method2 = (PsiMethod) element;

    matchContext.pushResult();

    try {
        if (method.getTypeParameters().length > 0) {
        result = match(
          method.getTypeParameterList(),
          ((PsiMethod)element).getTypeParameterList()
        );

        if (!result) return;
      }

      if (!checkHierarchy(method2,method)) {
        result = false;
        return;
      }

      result = (method.getName().equals(method2.getName()) || isTypedVar) &&
               match(method.getModifierList(),method2.getModifierList()) &&
               matchSons(method.getParameterList(),method2.getParameterList()) &&
               match(method.getReturnTypeElement(),method2.getReturnTypeElement()) &&
               matchInAnyOrder(method.getThrowsList(),method2.getThrowsList()) &&
               matchSonsOptionally( method.getBody(), method2.getBody() );
    } finally {
      MatchResultImpl ourResult = matchContext.hasResult() ? matchContext.getResult():null;
      matchContext.popResult();

      if (result) {
        if (isTypedVar) {
          final SubstitutionHandler handler = (SubstitutionHandler) matchContext.getPattern().getHandler(method.getNameIdentifier());
          if (ourResult != null) ourResult.setScopeMatch(true);
          handler.setNestedResult( ourResult );
          result = handler.handle(method2.getNameIdentifier(),matchContext);

          if (handler.getNestedResult() != null) { // some constraint prevent from adding
            handler.setNestedResult(null);
            copyResults(ourResult);
          }
        } else if (ourResult != null) {
          copyResults(ourResult);
        }
      }
    }
  }

  private void copyResults(final MatchResultImpl ourResult) {
    //if (ourResult.isMultipleMatch()) {
      if (ourResult.hasSons()) {
        for(MatchResult son:ourResult.getAllSons()) {
          matchContext.getResult().addSon((MatchResultImpl)son);
        }
      }
    //} else {
   //   matchContext.getResult().addSon(ourResult);
   // }
  }

  public static final String getText(final PsiElement match, int start,int end) {
    final String matchText = match.getText();
    if (start==0 && end==-1) return matchText;
    return matchText.substring(start,end == -1? matchText.length():end);
  }

  public void visitXmlAttribute(XmlAttribute attribute) {
    final XmlAttribute another = (XmlAttribute)element;
    final boolean isTypedVar = matchContext.getPattern().isTypedVar(attribute.getName());

    result = (attribute.getName().equals(another.getName()) || isTypedVar);
    if (result) {
      result = match(attribute.getValueElement(), another.getValueElement());
    }

    if (result && isTypedVar) {
      Handler handler = matchContext.getPattern().getHandler( attribute.getName() );
      result = ((SubstitutionHandler)handler).handle(another,matchContext);
    }
  }

  public void visitXmlAttributeValue(XmlAttributeValue value) {
    final XmlAttributeValue another = (XmlAttributeValue) element;
    final String text = StringUtil.stripQuotesAroundValue( value.getText() );
    
    final boolean isTypedVar = matchContext.getPattern().isTypedVar(text);

    if (isTypedVar) {
      Handler handler = matchContext.getPattern().getHandler( text );
      String text2 = another.getText();
      int offset = (text2.length() > 0 && ( text2.charAt(0) == '"' || text2.charAt(0) == '\''))? 1:0;
      result = ((SubstitutionHandler)handler).handle(another,offset,text2.length()-offset,matchContext);
    } else {
      result = text.equals(StringUtil.stripQuotesAroundValue(another.getText()));
    }
  }

  public void visitXmlTag(XmlTag tag) {
    final XmlTag another = (XmlTag)element;
    final boolean isTypedVar = matchContext.getPattern().isTypedVar(tag.getName());

    result = (tag.getName().equals(another.getName()) || isTypedVar) &&
             matchInAnyOrder(tag.getAttributes(),another.getAttributes());

    if(result && tag.getValue()!=null) {
      final XmlTagChild[] contentChildren = tag.getValue().getChildren();

      if (contentChildren != null && contentChildren.length > 0) {
        result = matchSequentially(
          new ArrayBackedNodeIterator(contentChildren),
          new ArrayBackedNodeIterator(another.getValue()!=null ? another.getValue().getChildren():XmlTagChild.EMPTY_ARRAY)
        );
      }
    }

    if (result && isTypedVar) {
      Handler handler = matchContext.getPattern().getHandler( tag.getName() );
      result = ((SubstitutionHandler)handler).handle(another,matchContext);
    }
  }

  public void visitXmlText(XmlText text) {
    result = matchSequentially(text.getFirstChild(),element.getFirstChild());
  }

  public void visitXmlToken(XmlToken token) {
    if (token.getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      String text = token.getText();
      final boolean isTypedVar = matchContext.getPattern().isTypedVar(text);

      if (isTypedVar) {
        result = handleTypedElement(token, element);
      } else {
        result = text.equals(element.getText());
      }
    }
  }
}
