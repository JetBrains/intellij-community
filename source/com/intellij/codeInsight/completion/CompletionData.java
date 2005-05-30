package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

import java.io.File;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 16:58:32
 * To change this template use Options | File Templates.
 */
public class CompletionData
 implements JDOMExternalizable{
  public static final Namespace COMPLETION_NS = Namespace.getNamespace("http://www.intellij.net/data/completion");
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionData");
  private final Set<Class> myFinalScopes = new HashSet<Class>();
  private final List<CompletionVariant> myCompletionVariants = new ArrayList<CompletionVariant>();


  public CompletionData(final String fileName){
    try{
      final Document document = JDOMUtil.loadDocument(CompletionData.class.getResourceAsStream(File.separator + fileName));
      readExternal(document.getRootElement());
    }
    catch(Exception ioe){
      LOG.error(ioe);
    }
  }

  public CompletionData(){ }

  public final void declareFinalScope(Class scopeClass){
    myFinalScopes.add(scopeClass);
  }

  protected final boolean isScopeFinal(Class scopeClass){
    if(myFinalScopes.contains(scopeClass))
      return true;

    final Iterator<Class> iter = myFinalScopes.iterator();
    while(iter.hasNext()){
      if(iter.next().isAssignableFrom(scopeClass)){
        return true;
      }
    }
    return false;
  }

  public boolean isScopeAcceptable(PsiElement scope){
    final Iterator<CompletionVariant> iter = myCompletionVariants.iterator();

    while(iter.hasNext()){
      final CompletionVariant variant = iter.next();
      if(variant.isScopeAcceptable(scope)){
        return true;
      }
    }
    return false;
  }

  public void defineScopeEquivalence(Class scopeClass, Class equivClass){
    final Iterator<CompletionVariant> iter = myCompletionVariants.iterator();
    if(isScopeFinal(scopeClass)){
      declareFinalScope(equivClass);
    }

    while(iter.hasNext()){
      final CompletionVariant variant = iter.next();
      if(variant.isScopeClassAcceptable(scopeClass)){
        variant.includeScopeClass(equivClass, variant.isScopeClassFinal(scopeClass));
      }
    }
    return;
  }

  public void registerVariant(CompletionVariant variant){
    myCompletionVariants.add(variant);
  }

  public void completeReference(PsiReference reference, LinkedHashSet set, CompletionContext context, PsiElement position){
    final CompletionVariant[] variants = findVariants(position, context);
    boolean haveApplicableVariants = false;

    for(int i = 0; i < variants.length; i++){
      if(variants[i].hasReferenceFilter()){
        variants[i].addReferenceCompletions(reference, position, set, context.prefix);
        haveApplicableVariants = true;
      }
    }

    if(!haveApplicableVariants){
      myGenericVariant.addReferenceCompletions(reference, position, set, context.prefix);
    }
  }

  public void addKeywordVariants(Set<CompletionVariant> set, CompletionContext context, PsiElement position){
    CompletionVariant[] variants = findVariants(position, context);
    for(int i = 0; i < variants.length; i++){
      if(!set.contains(variants[i]))
        set.add(variants[i]);
    }
  }

  public static void completeKeywordsBySet(LinkedHashSet set, Set<CompletionVariant> variants, CompletionContext context, PsiElement position){
    final Iterator<CompletionVariant> iter = variants.iterator();
    while(iter.hasNext())
      iter.next().addKeywords(context.file.getManager().getElementFactory(), set, context, position);
  }

  public LookupItemPreferencePolicy completeLocalVariableName(LinkedHashSet set, CompletionContext context, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    final VariableKind variableKind = CodeStyleManager.getInstance(var.getProject()).getVariableKind(var);
    final CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx) CodeStyleManager.getInstance(context.project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    LookupItemUtil.addLookupItems(set, suggestedNames, context.prefix);

    if (set.isEmpty()) {
      suggestedNameInfo = new SuggestedNameInfo(CompletionUtil.getOverlappedNameVersions(context.prefix, suggestedNames, "")) {
        public void nameChoosen(String name) {
        }
      };

      LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, context.prefix);
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
    if(parent == null) parent = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    LookupItemUtil.addLookupItems(set, CompletionUtil.getUnserolvedReferences(parent, false), context.prefix);
    LookupItemUtil.addLookupItems(set, StatisticsManager.getInstance().getNameSuggestions(var.getType(), StatisticsManager.getContext(var), context.prefix), context.prefix);

    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public LookupItemPreferencePolicy completeFieldName(LinkedHashSet set, CompletionContext context, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx) CodeStyleManager.getInstance(context.project);
    final String prefix = context.prefix;
    if(var.getType() == PsiType.VOID || prefix.startsWith("is") || prefix.startsWith("get") || prefix.startsWith("set")) return null;
    final VariableKind variableKind = CodeStyleManager.getInstance(var.getProject()).getVariableKind(var);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    LookupItemUtil.addLookupItems(set, suggestedNames, prefix);

    if (set.isEmpty()) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(variableKind);
      if(variableKind != VariableKind.STATIC_FINAL_FIELD){
        for (int i = 0; i < suggestedNames.length; i++)
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], variableKind);
      }


        suggestedNameInfo = new SuggestedNameInfo(CompletionUtil.getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix)) {
        public void nameChoosen(String name) {
        }
      };

      LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, prefix);
    }
    LookupItemUtil.addLookupItems(set, StatisticsManager.getInstance().getNameSuggestions(var.getType(), StatisticsManager.getContext(var), prefix), prefix);
    LookupItemUtil.addLookupItems(set, CompletionUtil.getUnserolvedReferences(var.getParent(), false), context.prefix);

    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public LookupItemPreferencePolicy completeMethodName(LinkedHashSet set, CompletionContext context, PsiElement element){
    if(element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.isConstructor()) {
        final PsiClass containingClass = method.getContainingClass();
        LookupItemUtil.addLookupItem(set, containingClass.getName(), context.prefix);
        return null;
      }
    }

    LookupItemUtil.addLookupItems(set, CompletionUtil.getUnserolvedReferences(element.getParent(), true), context.prefix);
    if(!((PsiModifierListOwner)element).hasModifierProperty("private")){
      LookupItemUtil.addLookupItems(set, CompletionUtil.getOverides((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)), context.prefix);
      LookupItemUtil.addLookupItems(set, CompletionUtil.getImplements((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)), context.prefix);
    }
    LookupItemUtil.addLookupItems(set, CompletionUtil.getPropertiesHandlersNames(
      (PsiClass)element.getParent(),
      ((PsiModifierListOwner)element).hasModifierProperty("static"),
      PsiUtil.getTypeByPsiElement(element), element), context.prefix);
    return null;
  }

  public LookupItemPreferencePolicy completeClassName(LinkedHashSet set, CompletionContext context, PsiClass aClass){
    return null;
  }

  public String findPrefix(PsiElement insertedElement, int offset){
    return findPrefixStatic(insertedElement, offset);
  }

  public CompletionVariant[] findVariants(final PsiElement position, final CompletionContext context){
    final List<CompletionVariant> variants = new ArrayList<CompletionVariant>();
    PsiElement scope = position;
    if(scope == null){
      scope = context.file;
    }
    while (scope != null) {
      boolean breakFlag = false;
      if (isScopeAcceptable(scope)){
        final Iterator<CompletionVariant> iter = myCompletionVariants.iterator();

        while(iter.hasNext()){
          final CompletionVariant variant = iter.next();
          if(variant.isVariantApplicable(position, scope) && !variants.contains(variant)){
            variants.add(variant);
            if(variant.isScopeFinal(scope))
              breakFlag = true;
          }
        }
      }
      if(breakFlag || isScopeFinal(scope.getClass()))
        break;
      scope = scope.getContext();
      if (scope instanceof PsiDirectory) break;
    }
    return variants.toArray(new CompletionVariant[variants.size()]);
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    final Iterator iter  = element.getChildren("variant", COMPLETION_NS).iterator();
    while(iter.hasNext()){
      final Element variantElement = (Element) iter.next();
      final CompletionVariant variant = new CompletionVariant();
      variant.readExternal(variantElement);
      registerVariant(variant);
    }
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Completion data could _not_ be written");
  }

  protected static final CompletionVariant myGenericVariant = new CompletionVariant(){
    public void addReferenceCompletions(PsiReference reference, PsiElement position, LinkedHashSet set, String prefix){
      addReferenceCompletions(reference, position, set, prefix, new CompletionVariantItem(TrueFilter.INSTANCE, TailType.NONE));
    }
  };

  public static String findPrefixStatic(PsiElement insertedElement, int offset) {
    if(insertedElement == null) return "";
    int offsetInElement = offset - insertedElement.getTextRange().getStartOffset();
    final PsiReference ref = insertedElement.findReferenceAt(offsetInElement);

    // TODO: need to add more separators here. Such as #, $ etc... Think on centralizing their location.
    final String text = insertedElement.getText();
    if(ref == null && (StringUtil.endsWithChar(text, '#') || StringUtil.endsWithChar(text, '.'))){
      return "";
    }

    if(ref instanceof PsiJavaCodeReferenceElement){
      final PsiElement name = ((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
      if(name != null){
        offsetInElement = offset - name.getTextRange().getStartOffset();
        return name.getText().substring(0, offsetInElement);
      }
      return "";
    }
    else if(ref != null){
      offsetInElement = offset - ref.getElement().getTextRange().getStartOffset();

      String result = ref.getElement().getText().substring(ref.getRangeInElement().getStartOffset(), offsetInElement);
      if(result.indexOf('(') > 0){
        result = result.substring(0, result.indexOf('('));
      }
      return result;
    }

    if (insertedElement instanceof PsiIdentifier || insertedElement instanceof PsiKeyword
        || "null".equals(text)
        || "true".equals(text)
        || "false".equals(text)
        || (insertedElement instanceof XmlToken
            && (((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
                ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS ||
                ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_NAME))
        ){
      return text.substring(0, offsetInElement).trim();
    }

    if (insertedElement instanceof PsiDocToken) {
      final PsiDocToken token = ((PsiDocToken)insertedElement);
      if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        return text.substring(0, offsetInElement).trim();
      }
      else if (token.getTokenType() == JavaDocTokenType.DOC_TAG_NAME) {
        return text.substring(1, offsetInElement).trim();
      }
    }

    return "";
  }
}
