package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.completion.scope.CompletionProcessor;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.JavaClassReferenceQuickFixProvider;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.jsp.jspJava.JspxStaticImportStatement;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:30:38
 * To change this template use Options | File Templates.
 */
public class JavaClassReferenceProvider extends GenericReferenceProvider implements CustomizableReferenceProvider {
  private static final Logger LOG =
    Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider");
  private static final char SEPARATOR = '.';
  private static final char SEPARATOR2 = '$';
  public static final ReferenceType CLASS_REFERENCE_TYPE = new ReferenceType(ReferenceType.JAVA_CLASS);

  private @Nullable Map<CustomizationKey, Object> myOptions;
  private boolean mySoft;

  public static final CustomizationKey<Boolean> RESOLVE_QUALIFIED_CLASS_NAME =
    new CustomizationKey<Boolean>(PsiBundle.message("qualified.resolve.class.reference.provider.option"));

  public static final CustomizationKey<String[]> EXTEND_CLASS_NAMES = new CustomizationKey<String[]>("EXTEND_CLASS_NAMES");
  public static final CustomizationKey<Boolean> INSTANTIATABLE = new CustomizationKey<Boolean>("INSTANTIATABLE");
  public static final CustomizationKey<Boolean> RESOLVE_ONLY_CLASSES = new CustomizationKey<Boolean>("RESOLVE_ONLY_CLASSES");


  public JavaClassReferenceProvider(String extendClassName, boolean instantiatable) {
    this(new String[]{extendClassName}, instantiatable);
  }

  public JavaClassReferenceProvider(String[] extendClassNames, boolean instantiatable) {
    myOptions = new HashMap<CustomizationKey, Object>();
    EXTEND_CLASS_NAMES.putValue(myOptions, extendClassNames);
    INSTANTIATABLE.putValue(myOptions, instantiatable);
    RESOLVE_ONLY_CLASSES.putValue(myOptions, Boolean.TRUE);
  }

  public JavaClassReferenceProvider(@NotNull String extendClassName) {
    this(extendClassName, true);
  }

  public boolean isSoft() {
    return mySoft;
  }

  public void setSoft(final boolean soft) {
    mySoft = soft;
  }

  public JavaClassReferenceProvider() {
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    return getReferencesByElement(element, CLASS_REFERENCE_TYPE);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    String text = element.getText();

    if (element instanceof XmlAttributeValue) {
      final String valueString = ((XmlAttributeValue)element).getValue();
      int startOffset = StringUtil.startsWithChar(text, '"') || StringUtil.startsWithChar(text, '\'') ? 1 : 0;
      return getReferencesByString(valueString, element, type, startOffset);
    }
    else if (element instanceof XmlTag) {
      final XmlTagValue value = ((XmlTag)element).getValue();

      text = value.getText();
      final String trimmedText = text.trim();

      return getReferencesByString(trimmedText, element, type,
                                   value.getTextRange().getStartOffset() + text.indexOf(trimmedText) - element.getTextOffset());
    }

    return getReferencesByString(text, element, type, 0);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return new ReferenceSet(str, position, offsetInPosition, type, false).getAllReferences();
  }

  private static final SoftReference<List<PsiElement>> NULL_REFERENCE = new SoftReference<List<PsiElement>>(null);
  private SoftReference<List<PsiElement>> myDefaultPackageContent = NULL_REFERENCE;
  private Runnable myPackagesEraser = null;

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    if (position == null) return;
    if (hint == null || hint.shouldProcess(PsiPackage.class) || hint.shouldProcess(PsiClass.class)) {
      final List<PsiElement> cachedPackages = getDefaultPackages(position);
      for (final PsiElement psiPackage : cachedPackages) {
        if (!processor.execute(psiPackage, PsiSubstitutor.EMPTY)) return;
      }
    }
  }

  protected List<PsiElement> getDefaultPackages(PsiElement position) {
    List<PsiElement> cachedPackages = myDefaultPackageContent.get();
    if (cachedPackages == null) {
      final List<PsiElement> psiPackages = new ArrayList<PsiElement>();
      final PsiManager manager = position.getManager();
      final PsiPackage rootPackage = manager.findPackage("");
      if (rootPackage != null) {
        rootPackage.processDeclarations(new BaseScopeProcessor() {
          public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
            psiPackages.add(element);
            return true;
          }
        }, PsiSubstitutor.EMPTY, position, position);
      }
      if (myPackagesEraser == null) {
        myPackagesEraser = new Runnable() {
          public void run() {
            myDefaultPackageContent = NULL_REFERENCE;
          }
        };
      }
      cachedPackages = psiPackages;
      ((PsiManagerImpl)manager).registerWeakRunnableToRunOnChange(myPackagesEraser);
      myDefaultPackageContent = new SoftReference<List<PsiElement>>(cachedPackages);
    }
    return cachedPackages;
  }

  public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
    myOptions = options;
  }

  @Nullable
  public Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }

  protected class ReferenceSet {
    private final @Nullable Map<CustomizationKey, Object> options = myOptions;
    private PsiReference[] myReferences;
    private PsiElement myElement;
    private final int myStartInElement;
    private final ReferenceType myType;

    ReferenceSet(String str, PsiElement element, int startInElement, ReferenceType type, final boolean isStatic) {
      myType = type;
      myStartInElement = startInElement;
      reparse(str, element, isStatic);
    }

    private void reparse(String str, PsiElement element, final boolean isStaticImport) {
      myElement = element;
      final List<JavaReference> referencesList = new ArrayList<JavaReference>();
      int currentDot = -1;
      int index = 0;
      boolean allowDollarInNames = element.getLanguage()instanceof XMLLanguage;

      while (true) {
        int nextDotOrDollar = str.indexOf(SEPARATOR, currentDot + 1);
        if (nextDotOrDollar == -1 && allowDollarInNames) {
          nextDotOrDollar = str.indexOf(SEPARATOR2, currentDot + 1);
        }
        final String subreferenceText =
          nextDotOrDollar > 0 ? str.substring(currentDot + 1, nextDotOrDollar) : str.substring(currentDot + 1);

        TextRange textRange =
          new TextRange(myStartInElement + currentDot + 1, myStartInElement + (nextDotOrDollar > 0 ? nextDotOrDollar : str.length()));
        JavaReference currentContextRef = new JavaReference(textRange, index++, subreferenceText, isStaticImport);
        referencesList.add(currentContextRef);
        if ((currentDot = nextDotOrDollar) < 0) {
          break;
        }
      }

      myReferences = referencesList.toArray(new JavaReference[referencesList.size()]);
    }

    private void reparse(PsiElement element, final TextRange range) {
      final String text = element.getText().substring(range.getStartOffset(), range.getEndOffset());

      //if (element instanceof XmlAttributeValue) {
      //  text = StringUtil.stripQuotesAroundValue(element.getText().substring(range.getStartOffset(), range.getEndOffset()));
      //}
      //else if (element instanceof XmlTag) {
      //  text = ((XmlTag)element).getValue().getTrimmedText();
      //}
      //else {
      //  text = element.getText();
      //}
      reparse(text, element, false);
    }

    private PsiReference getReference(int index) {
      return myReferences[index];
    }

    protected PsiReference[] getAllReferences() {
      return myReferences;
    }

    private ReferenceType getType(int index) {
      if (index != myReferences.length - 1) {
        return new ReferenceType(ReferenceType.JAVA_CLASS, ReferenceType.JAVA_PACKAGE);
      }
      return myType;
    }

    public class JavaReference extends GenericReference implements PsiJavaReference, QuickFixProvider {
      private final int myIndex;
      private TextRange myRange;
      private final String myText;
      private final boolean myInStaticImport;

      public JavaReference(TextRange range, int index, String text, final boolean staticImport) {
        super(JavaClassReferenceProvider.this);
        myInStaticImport = staticImport;
        LOG.assertTrue(range.getEndOffset() <= myElement.getTextLength());
        myIndex = index;
        myRange = range;
        myText = text;
      }

      @Nullable
      public PsiElement getContext() {
        final PsiReference contextRef = getContextReference();
        return contextRef != null ? contextRef.resolve() : null;
      }

      public void processVariants(final PsiScopeProcessor processor) {

        if (processor instanceof CompletionProcessor && EXTEND_CLASS_NAMES.getValue(myOptions) != null) {
          ((CompletionProcessor)processor).setCompletionElements(getVariants());
          return;
        }
        PsiScopeProcessor processorToUse = processor;

        if (myInStaticImport) {
          // allows to complete members
          processor.handleEvent(PsiScopeProcessor.Event.CHANGE_LEVEL, null);
        }
        else if (isStaticClassReference()) {
          processor.handleEvent(PsiScopeProcessor.Event.START_STATIC, null);
          processorToUse = new PsiScopeProcessor() {
            public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
              if (element instanceof PsiClass) processor.execute(element, substitutor);
              return true;
            }

            public <V> V getHint(Class<V> hintClass) {
              return processor.getHint(hintClass);
            }

            public void handleEvent(Event event, Object associated) {
              processor.handleEvent(event, associated);
            }
          };
        }
        super.processVariants(processorToUse);
      }

      private boolean isStaticClassReference() {
        final String s = getElement().getText();
        return isStaticClassReference(s);
      }

      private boolean isStaticClassReference(final String s) {
        return myIndex > 0 && s.charAt(getRangeInElement().getStartOffset() - 1) == SEPARATOR2;
      }

      @Nullable
      public PsiReference getContextReference() {
        return myIndex > 0 ? getReference(myIndex - 1) : null;
      }

      public ReferenceType getType() {
        return ReferenceSet.this.getType(myIndex);
      }

      public ReferenceType getSoftenType() {
        return new ReferenceType(ReferenceType.JAVA_CLASS, ReferenceType.JAVA_PACKAGE);
      }

      public boolean needToCheckAccessibility() {
        return false;
      }

      public PsiElement getElement() {
        return myElement;
      }

      public boolean isReferenceTo(PsiElement element) {
        return (element instanceof PsiClass || element instanceof PsiPackage) && super.isReferenceTo(element);
      }

      public TextRange getRangeInElement() {
        return myRange;
      }

      public String getCanonicalText() {
        return myText;
      }

      public boolean isSoft() {
        return ReferenceSet.this.isSoft();
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
        if (manipulator != null) {
          final PsiElement element = manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName);
          myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
          return element;
        }
        throw new IncorrectOperationException("Manipulator for this element is not defined");
      }

      public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        if (isReferenceTo(element)) return getElement();

        final String qualifiedName;
        if (element instanceof PsiClass) {
          PsiClass psiClass = (PsiClass)element;
          qualifiedName = psiClass.getQualifiedName();
        }
        else if (element instanceof PsiPackage) {
          PsiPackage psiPackage = (PsiPackage)element;
          qualifiedName = psiPackage.getQualifiedName();
        }
        else {
          throw new IncorrectOperationException("Cannot bind to " + element);
        }

        final String newName = qualifiedName;
        TextRange range = new TextRange(getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
        final PsiElement finalElement = getManipulator(getElement()).handleContentChange(getElement(), range, newName);
        range = new TextRange(range.getStartOffset(), range.getStartOffset() + newName.length());
        reparse(finalElement, range);
        return finalElement;
      }

      public PsiElement resolveInner() {
        return advancedResolve(true).getElement();
      }

      public Object[] getVariants() {
        PsiElement context = getContext();
        if (context == null) {
          context = myElement.getManager().findPackage("");
        }
        if (context instanceof PsiPackage) {
          final String[] extendClasses = EXTEND_CLASS_NAMES.getValue(myOptions);
          if (extendClasses != null) {
            return getSubclassVariants((PsiPackage)context, extendClasses);
          }
          return processPackage((PsiPackage)context);
        }
        if (context instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)context;

          if (myInStaticImport) {
            return ArrayUtil.mergeArrays(aClass.getInnerClasses(), aClass.getFields(), Object.class);
          }
          else if (isStaticClassReference()) {
            final PsiClass[] psiClasses = aClass.getInnerClasses();
            final List<PsiClass> staticClasses = new ArrayList<PsiClass>(psiClasses.length);

            for (PsiClass c : psiClasses) {
              if (c.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                staticClasses.add(c);
              }
            }
            return staticClasses.size() > 0 ? staticClasses.toArray(new PsiClass[staticClasses.size()]) : PsiClass.EMPTY_ARRAY;
          }
        }
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      private Object[] processPackage(final PsiPackage aPackage) {
        final PsiPackage[] subPackages = aPackage.getSubPackages();
        final PsiClass[] classes = aPackage.getClasses();
        return ArrayUtil.mergeArrays(subPackages, classes, Object.class);
      }

      @NotNull
      public JavaResolveResult advancedResolve(boolean incompleteCode) {
        if (!myElement.isValid()) return JavaResolveResult.EMPTY;

        final String elementText = getElement().getText();

        if (isStaticClassReference(elementText)) {
          final PsiElement psiElement = myReferences[myIndex - 1].resolve();

          if (psiElement instanceof PsiClass) {
            final PsiClass psiClass = ((PsiClass)psiElement).findInnerClassByName(getCanonicalText(), false);
            if (psiClass != null) return new ClassCandidateInfo(psiClass, PsiSubstitutor.EMPTY, false, false, myElement);
            return JavaResolveResult.EMPTY;
          }
        }

        String qName = elementText.substring(getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());

        PsiManager manager = myElement.getManager();
        if (myIndex == myReferences.length - 1) {
          final PsiClass aClass = manager.findClass(qName, GlobalSearchScope.allScope(myElement.getProject()));
          if (aClass != null) {
            return new ClassCandidateInfo(aClass, PsiSubstitutor.EMPTY, false, false, myElement);
          } else {
            final Boolean value = RESOLVE_ONLY_CLASSES.getValue(myOptions);
            if (value != null && value.booleanValue()) {
              return JavaResolveResult.EMPTY;
            }
          }
        }
        PsiElement resolveResult = manager.findPackage(qName);
        if (resolveResult == null) {
          resolveResult = manager.findClass(qName, GlobalSearchScope.allScope(myElement.getProject()));
        }
        if (myInStaticImport && resolveResult == null) {
          resolveResult = JspxStaticImportStatement.resolveMember(qName, manager, getElement().getResolveScope());
        }
        if (resolveResult == null) {
          PsiFile containingFile = myElement.getContainingFile();

          if (containingFile instanceof PsiJavaFile) {
            if (containingFile instanceof JspFile) {
              containingFile = containingFile.getViewProvider().getPsi(StdLanguages.JAVA);
              if (containingFile == null) return JavaResolveResult.EMPTY;
            }
            
            final ClassResolverProcessor processor = new ClassResolverProcessor(getCanonicalText(), myElement);
            containingFile.processDeclarations(processor, PsiSubstitutor.EMPTY, null, myElement);

            if (processor.getResult().length == 1) {
              final JavaResolveResult javaResolveResult = processor.getResult()[0];

              if (javaResolveResult != JavaResolveResult.EMPTY && options != null) {
                final Boolean value = RESOLVE_QUALIFIED_CLASS_NAME.getValue(options);
                if (value != null && value.booleanValue()) {
                  final String qualifiedName = ((PsiClass)javaResolveResult.getElement()).getQualifiedName();

                  if (!qName.equals(qualifiedName)) {
                    return JavaResolveResult.EMPTY;
                  }
                }
              }

              return javaResolveResult;
            }
          }
        }
        return resolveResult != null
               ? new ClassCandidateInfo(resolveResult, PsiSubstitutor.EMPTY, false, false, myElement)
               : JavaResolveResult.EMPTY;
      }

      @NotNull
      public JavaResolveResult[] multiResolve(boolean incompleteCode) {
        final JavaResolveResult javaResolveResult = advancedResolve(incompleteCode);
        if (javaResolveResult.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
        return new JavaResolveResult[]{javaResolveResult};
      }

      public void registerQuickfix(HighlightInfo info, PsiReference reference) {
        final String[] extendClasses = EXTEND_CLASS_NAMES.getValue(myOptions);
        if (extendClasses != null && extendClasses.length > 0) {
          JavaClassReferenceQuickFixProvider.registerQuickFix(info, reference, extendClasses[0]);
        } else {
          JavaClassReferenceQuickFixProvider.registerQuickFix(info, reference);
        }
      }

      @NotNull
      protected Object[] getSubclassVariants(PsiPackage context, String[] extendClasses) {
        HashSet<Object> lookups = new HashSet<Object>();
        GlobalSearchScope scope = GlobalSearchScope.packageScope(context, true);
        Boolean inst = INSTANTIATABLE.getValue(myOptions);
        boolean instantiatable = inst == null || inst.booleanValue();

        for (String extendClassName : extendClasses) {
          PsiClass extendClass = context.getManager().findClass(extendClassName, scope);
          if (extendClass != null) {
            PsiClass[] result = context.getManager().getSearchHelper().findInheritors(extendClass, scope, true);
            for (final PsiClass clazz : result) {
              Object value = createSubclassLookupValue(context, clazz, instantiatable);
              if (value != null) {
                lookups.add(value);
              }
            }
            // add itself
            Object value = createSubclassLookupValue(context, extendClass, instantiatable);
            if (value != null) {
              lookups.add(value);
            }
          }
        }
        return lookups.toArray();
      }

      @Nullable
      protected Object createSubclassLookupValue(final PsiPackage context, final PsiClass clazz, boolean instantiatable) {
        if (instantiatable && !PsiUtil.isInstantiatable(clazz)) {
          return null;
        }
        String name = clazz.getQualifiedName();
        if (name == null) return null;
        final String pack = context.getQualifiedName();
        if (pack.length() > 0) {
          name = name.substring(pack.length() + 1);
        }
        return LookupValueFactory.createLookupValue(name, clazz.getIcon(Iconable.ICON_FLAG_READ_STATUS));
       }
    }


    protected boolean isSoft() {
      return mySoft;
    }
  }
}
