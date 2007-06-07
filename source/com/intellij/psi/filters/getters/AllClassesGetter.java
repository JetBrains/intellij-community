package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.codeInsight.completion.simple.SimpleLookupItem;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 02.12.2003
 * Time: 16:49:25
 * To change this template use Options | File Templates.
 */
public class AllClassesGetter implements ContextGetter{
  @NonNls private static final String JAVA_PACKAGE_PREFIX = "java.";
  @NonNls private static final String JAVAX_PACKAGE_PREFIX = "javax.";
  private final ElementFilter myFilter;
  private static final SimpleInsertHandler INSERT_HANDLER = new SimpleInsertHandler() {
    public int handleInsert(final Editor editor, final int startOffset, final SimpleLookupItem item, final LookupItem[] allItems, final TailType tailType) {
      final PsiClass psiClass = (PsiClass)item.getObject();
      final int endOffset = editor.getCaretModel().getOffset();
      final String qname = psiClass.getQualifiedName();
      if (qname == null) return endOffset;

      if (endOffset == 0) return endOffset;

      final Document document = editor.getDocument();
      final PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document);
      final PsiElement element = file.findElementAt(endOffset - 1);
      if (element == null || !(element instanceof XmlElement)) return endOffset;

      int i = endOffset - 1;
      while (i >= 0) {
        final char ch = document.getCharsSequence().charAt(i);
        if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
        i--;
      }
      document.replaceString(i + 1, endOffset, qname);
      return endOffset;
    }
  };

  public AllClassesGetter(final ElementFilter filter) {
    myFilter = filter;
  }

  public Object[] get(final PsiElement context, CompletionContext completionContext) {
    if(context == null || !context.isValid()) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    String prefix = context.getText().substring(0, completionContext.startOffset - context.getTextRange().getStartOffset());
    final int i = prefix.lastIndexOf('.');
    String packagePrefix = "";
    if (i > 0) {
      packagePrefix = prefix.substring(0, i);
    }

    final PsiManager manager = context.getManager();
    final Set<PsiClass> classesSet = new THashSet<PsiClass>(new TObjectHashingStrategy<PsiClass>() {
      public int computeHashCode(final PsiClass object) {
        final String name = object.getQualifiedName();
        return name != null ? name.hashCode() : 0;
      }

      public boolean equals(final PsiClass o1, final PsiClass o2) {
        return manager.areElementsEquivalent(o1, o2);
      }
    });

    final PsiShortNamesCache cache = manager.getShortNamesCache();

    final GlobalSearchScope scope = context.getContainingFile().getResolveScope();
    final String[] names = cache.getAllClassNames(true);
    
    boolean lookingForAnnotations = false;
    final PsiElement prevSibling = context.getParent().getPrevSibling();
    if (prevSibling instanceof PsiJavaToken && 
        ((PsiJavaToken)prevSibling).getTokenType() == JavaTokenType.AT) {
      lookingForAnnotations = true;
    }

    for (final String name : names) {
      if (!completionContext.prefixMatches(name)) continue;

      for (PsiClass psiClass : cache.getClassesByName(name, scope)) {
        if (lookingForAnnotations && !psiClass.isAnnotationType()) continue;

        if (CompletionUtil.isInExcludedPackage(psiClass)) continue;

        final String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(packagePrefix)) continue;

        if (!myFilter.isAcceptable(psiClass, context)) continue;

        classesSet.add(psiClass);
      }
    }

    List<PsiClass> classesList = new ArrayList<PsiClass>(classesSet);

    Collections.sort(classesList, new Comparator<PsiClass>() {
      public int compare(PsiClass psiClass, PsiClass psiClass1) {
        if(manager.areElementsEquivalent(psiClass, psiClass1)) return 0;

        return getClassIndex(psiClass) - getClassIndex(psiClass1);
      }

      private int getClassIndex(PsiClass psiClass){
        if(psiClass.getManager().isInProject(psiClass)) return 2;
        final String qualifiedName = psiClass.getQualifiedName();
        if(qualifiedName.startsWith(JAVA_PACKAGE_PREFIX) ||
           qualifiedName.startsWith(JAVAX_PACKAGE_PREFIX)) return 1;
        return 0;
      }

      public boolean equals(Object o) {
        return o == this;
      }
    });

    return ContainerUtil.map2Array(classesList, SimpleLookupItem.class, new NotNullFunction<PsiClass, SimpleLookupItem>() {
      @NotNull
      public SimpleLookupItem fun(final PsiClass psiClass) {
        final SimpleLookupItem item = new SimpleLookupItem(psiClass);
        if (context instanceof XmlElement) {
          item.setInsertHandler(INSERT_HANDLER);
        }
        return item;
      }
    });
  }
}
