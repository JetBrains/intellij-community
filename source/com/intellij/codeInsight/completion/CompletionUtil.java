package com.intellij.codeInsight.completion;

import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.position.SuperParentFilter;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

public class CompletionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionUtil");

  public static final Key TAIL_TYPE_ATTR = Key.create("tailType"); // one of constants defined in TailType interface

  private static final Key<SmartPsiElementPointer> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  private static final CompletionData ourJavaCompletionData = new JavaCompletionData();
  private static final CompletionData ourJava15CompletionData = new Java15CompletionData();
  private static final CompletionData ourJSPCompletionData = new JSPCompletionData();
  private static final CompletionData ourXmlCompletionData = new XmlCompletionData();
  private static final CompletionData ourGenericCompletionData = new WordCompletionData();
  private static final CompletionData ourWordCompletionData = new WordCompletionData();
  private static final CompletionData ourJavaDocCompletionData = new JavaDocCompletionData();

  static {
    registerCompletionData(StdFileTypes.JSPX,new JspxCompletionData());
  }

  private static HashMap<FileType,CompletionData> completionDatas;

  private static void initBuiltInCompletionDatas() {
    completionDatas = new HashMap<FileType, CompletionData>();
    registerCompletionData(StdFileTypes.HTML,new HtmlCompletionData());
    registerCompletionData(StdFileTypes.XHTML,new XHtmlCompletionData());
  }

  public static final String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz ";
  public static final Key<String> COMPLETION_PREFIX = Key.create("Completion prefix");
  public static final Key<PsiElement> ORIGINAL_KEY = Key.create("ORIGINAL_KEY");
  public static final Key<PsiElement> COPY_KEY = Key.create("COPY_KEY");

  public static PsiType getQualifierType(LookupItem item){
    return (PsiType) item.getAttribute(CompletionUtil.QUALIFIER_TYPE_ATTR);
  }

  public static void setQualifierType(LookupItem item, PsiType type){
    if (type != null){
      item.setAttribute(QUALIFIER_TYPE_ATTR, type);
    }
    else{
      item.setAttribute(QUALIFIER_TYPE_ATTR, null);
    }
  }

  public static boolean startsWith(String text, String prefix) {
    //if (text.length() <= prefix.length()) return false;
    return toLowerCase(text).startsWith(toLowerCase(prefix));
  }

  private static String toLowerCase(String text) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    switch (settings.COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return text.toLowerCase();

      case CodeInsightSettings.FIRST_LETTER:
        {
          StringBuffer buffer = new StringBuffer();
          buffer.append(text.toLowerCase());
          if (buffer.length() > 0) {
            buffer.setCharAt(0, text.charAt(0));
          }
          return buffer.toString();
        }

      default:
        return text;
    }
  }

  static void highlightMembersOfContainer(LinkedHashSet set) {
    for (Iterator iter = set.iterator(); iter.hasNext();) {
      LookupItem item = (LookupItem)iter.next();
      Object o = item.getObject();
      PsiType qualifierType = getQualifierType(item);
      if (qualifierType == null) continue;
      if (qualifierType instanceof PsiArrayType) {
        if (o instanceof PsiField || o instanceof PsiMethod || o instanceof PsiClass) {
          PsiElement parent = ((PsiElement)o).getParent();
          if (parent instanceof PsiClass && parent.getContainingFile().getVirtualFile() == null) { //?
            item.setAttribute(LookupItem.HIGHLIGHTED_ATTR, "");
          }
        }
      }
      else if (qualifierType instanceof PsiClassType){
        PsiClass qualifierClass = ((PsiClassType) qualifierType).resolve();
        if (o instanceof PsiField || o instanceof PsiMethod || o instanceof PsiClass) {
          PsiElement parent = ((PsiElement)o).getParent();
          if (parent != null && parent.equals(qualifierClass)) {
            item.setAttribute(LookupItem.HIGHLIGHTED_ATTR, "");
          }
        }
      }
    }
  }

  public static final CompletionData getCompletionDataByElement(PsiElement element, CompletionContext context){
    final PsiFile file = context.file;
    if(element instanceof PsiComment) {
      return ourWordCompletionData;
    }
    if(element instanceof PsiJavaToken) {
      final PsiJavaToken token = (PsiJavaToken) element;
      if (token.getTokenType() == JavaTokenType.STRING_LITERAL) 
        return ourWordCompletionData;
    }

    if(file instanceof PsiAspectFile){
      return ourGenericCompletionData;
    }
    else if((file instanceof PsiJavaFile || file instanceof PsiCodeFragment) &&
            ! (file instanceof JspFile) // TODO: we need to check the java context of JspX
            ){
      if (element != null && new SuperParentFilter(new ClassFilter(PsiDocComment.class)).isAcceptable(element, element.getParent())){
        return ourJavaDocCompletionData;
      }
      else{
        return element != null && element.getManager().getEffectiveLanguageLevel() == LanguageLevel.JDK_1_5 ? ourJava15CompletionData : ourJavaCompletionData;
      }
    }
    else if(file instanceof JspFile &&
            !(file instanceof PsiJavaFile) // filter out jspx
            ){
      return ourJSPCompletionData;
    }
    else if(file instanceof XmlFile && file.getFileType()==StdFileTypes.XML) {
      return ourXmlCompletionData;
    } else {
      final CompletionData completionDataByFileType = getCompletionDataByFileType(file.getFileType());
      if (completionDataByFileType!=null) return completionDataByFileType;
    }
    return ourGenericCompletionData;
  }

  public static final void registerCompletionData(FileType fileType,CompletionData completionData) {
    if (completionDatas==null) initBuiltInCompletionDatas();
    completionDatas.put(fileType, completionData);
  }

  public static final CompletionData getCompletionDataByFileType(FileType fileType) {
    if (completionDatas==null) initBuiltInCompletionDatas();
    return completionDatas.get(fileType);
  }

  public static boolean checkName(String name, String prefix){
    return checkName(name,prefix,false);
  }
  
  public static boolean checkName(String name, String prefix, boolean forceCaseInsensitive){
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if(name == null)
      return false;
    boolean ret = true;
    if(prefix != null){
      int variant = settings.COMPLETION_CASE_SENSITIVE;
      variant = forceCaseInsensitive ? CodeInsightSettings.NONE:variant;

      switch(variant){
        case CodeInsightSettings.NONE:
          ret = name.toLowerCase().startsWith(prefix.toLowerCase());
          break;
        case CodeInsightSettings.FIRST_LETTER:
          if(name.length() > 0){
            if(prefix.length() > 0){
              ret = (name.charAt(0) == prefix.charAt(0))
                      && name.toLowerCase().startsWith(prefix.substring(1).toLowerCase(), 1);
            }
          }
          else{
            if(prefix.length() > 0) ret = false;
          }

          break;
        case CodeInsightSettings.ALL:
          ret = name.startsWith(prefix, 0);
          break;
        default:
          ret = false;
      }
    }
    return ret;
  }

  public static LookupItemPreferencePolicy completeVariableName(Project project, LinkedHashSet set, String prefix, PsiType varType,
                                                                VariableKind varKind) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx) CodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, prefix);

    if (set.isEmpty() && PsiType.VOID != varType) {
      boolean isMethodPrefix = prefix.startsWith("is") || prefix.startsWith("get") || prefix.startsWith("set");

      ArrayList newSuggestions = new ArrayList();
      String[] suggestedNames = suggestedNameInfo.names;
      int prefixLen = prefix.length();
      int longestOverlap = 0;
      String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
      int suffixLen = requiredSuffix.length();

      for (int i = 0; i < suggestedNames.length; i++) {
        String suggestedName = suggestedNames[i];
        String propertyName = varKind == VariableKind.STATIC_FINAL_FIELD && !isMethodPrefix
            ? suggestedName
            : codeStyleManager.variableNameToPropertyName(suggestedName, varKind);

        if(propertyName.toUpperCase().startsWith(prefix.toUpperCase())){
          newSuggestions.add(propertyName);
          longestOverlap = prefixLen;
        }
        propertyName = "" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        int overlap = 0;
        int propertyNameLen = propertyName.length();
        for (int j = 1; j < prefixLen && j < propertyNameLen; j++) {
          if (prefix.substring(prefixLen - j).equals(propertyName.substring(0, j))) {
            overlap = j;
          }
        }

        if (overlap < longestOverlap) continue;

        if (overlap > longestOverlap) {
          newSuggestions.clear();
          longestOverlap = overlap;
        }

        if (!isMethodPrefix && varKind == VariableKind.STATIC_FINAL_FIELD && overlap == 0 && prefix.charAt(prefixLen - 1) != '_') {
          prefix = prefix + '_';
          prefixLen++;
        }

        String suggestion = prefix.substring(0, prefixLen - overlap) + propertyName;

        int suggestionLength = suggestion.length();
        overlap = 0;
        for (int j = 1; j < suggestionLength && j < suffixLen; j++) {
          if (suggestion.substring(0, suggestionLength - j).endsWith(requiredSuffix)) {
            overlap = j;
          }
        }
        suggestion = suggestion.substring(0, suggestionLength - overlap) + requiredSuffix;

        if (!newSuggestions.contains(suggestion)) {
          newSuggestions.add(suggestion);
        }
      }

      suggestedNameInfo = new SuggestedNameInfo((String[]) newSuggestions.toArray(new String[newSuggestions.size()])) {
        public void nameChoosen(String name) {
        }
      };

      LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, prefix);
    }

    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public static PsiType eliminateWildcards (PsiType type) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = ((PsiClassType)type);
      ResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = (PsiClass)resolveResult.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
        PsiTypeParameter[] typeParams = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
        for (int j = 0; j < typeParams.length; j++) {
          PsiTypeParameter typeParam = typeParams[j];
          PsiType substituted = resolveResult.getSubstitutor().substitute(typeParam);
          if (substituted instanceof PsiWildcardType) {
            substituted = ((PsiWildcardType)substituted).getBound();
            if (substituted == null) substituted = PsiType.getJavaLangObject(manager, aClass.getResolveScope());
          }
          map.put(typeParam, substituted);
        }

        PsiElementFactory factory = manager.getElementFactory();
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        type = factory.createType(aClass, substitutor);
      }
    }
    return type;
  }
}
