package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactoryImpl;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.filters.getters.AllClassesGetter");

  @NonNls private static final String JAVA_PACKAGE_PREFIX = "java.";
  @NonNls private static final String JAVAX_PACKAGE_PREFIX = "javax.";
  private final ElementFilter myFilter;
  private static final SimpleInsertHandler INSERT_HANDLER = new SimpleInsertHandler() {
    public int handleInsert(final Editor editor, final int startOffset, final LookupElement item, final LookupElement[] allItems, final TailType tailType) {
      final PsiClass psiClass = (PsiClass)item.getObject();
      int endOffset = editor.getCaretModel().getOffset();
      final String qname = psiClass.getQualifiedName();
      if (qname == null) return endOffset;

      if (endOffset == 0) return endOffset;

      final Document document = editor.getDocument();
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(editor.getProject());
      final PsiFile file = psiDocumentManager.getPsiFile(document);
      final PsiElement element = file.findElementAt(endOffset - 1);
      if (element == null) return endOffset;


      boolean insertFqn = true;
      boolean insertSpace = endOffset < document.getTextLength() && Character.isJavaIdentifierPart(document.getCharsSequence().charAt(endOffset));
      if (insertSpace){
        document.insertString(endOffset, " ");
      }
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);
      if (psiReference != null) {
        final PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, resolveReference(psiReference))) {
          insertFqn = false;
        } else {
          try {
            final PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              final PsiElement psiElement = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                endOffset = psiElement.getTextRange().getEndOffset();
              }
              insertFqn = false;
            }
          } catch (IncorrectOperationException e) {
            //if it's empty we just insert fqn below
          }
        }
      }
      if (insertSpace){
        document.deleteString(endOffset, endOffset + 1);
      }

      if (insertFqn) {
        int i = endOffset - 1;
        while (i >= 0) {
          final char ch = document.getCharsSequence().charAt(i);
          if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
          i--;
        }
        document.replaceString(i + 1, endOffset, qname);
        endOffset = i + 1 + qname.length();
      }

      //todo[peter] hack, to deal with later
      if (psiClass.isAnnotationType()) {
        // Check if someone inserts annotation class that require @
        psiDocumentManager.commitDocument(document);
        PsiElement elementAt = file.findElementAt(startOffset);
        final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

        if (elementAt instanceof PsiIdentifier &&
            ( PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null || //we are inserting '@' only in annotation parameters
              (parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile) // top level annotation without @
            )
            && isAtTokenNeeded(editor, startOffset)) {
          PsiElement parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, PsiCodeBlock.class);
          if (parent == null && parentElement instanceof PsiErrorElement) {
            PsiElement nextElement = parentElement.getNextSibling();
            if (nextElement instanceof PsiWhiteSpace) nextElement = nextElement.getNextSibling();
            if (nextElement instanceof PsiClass) parent = nextElement;
          }

          if (parent instanceof PsiModifierListOwner) {
            document.insertString(elementAt.getTextRange().getStartOffset(), "@");
            endOffset++;
          }
        }
      }

      return endOffset;
    }

    private boolean isAtTokenNeeded(Editor editor, int startOffset) {
      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(startOffset);
      LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
      iterator.retreat();
      if (iterator.getTokenType() == JavaTokenType.WHITE_SPACE) iterator.retreat();
      return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
    }

  };

  private static PsiElement resolveReference(final PsiReference psiReference) {
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(true);
      if (results.length == 1) return results[0].getElement();
    }
    return psiReference.resolve();
  }

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

    return ContainerUtil.map2Array(classesList, (Class<LookupItem<PsiClass>>)(Class)LookupItem.class, new NotNullFunction<PsiClass, LookupItem<PsiClass>>() {
      @NotNull
      public LookupItem<PsiClass> fun(final PsiClass psiClass) {
        return createLookupItem(psiClass);
      }
    });
  }

  protected LookupItem<PsiClass> createLookupItem(final PsiClass psiClass) {
    return LookupElementFactoryImpl.getInstance().createLookupElement(psiClass).setInsertHandler(INSERT_HANDLER);
  }

}
