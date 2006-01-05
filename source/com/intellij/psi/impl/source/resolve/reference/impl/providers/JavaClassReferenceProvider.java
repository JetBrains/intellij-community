package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:30:38
 * To change this template use Options | File Templates.
 */
public class JavaClassReferenceProvider extends GenericReferenceProvider implements CustomizableReferenceProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider");
  private static final char SEPARATOR = '.';
  private static final ReferenceType ourType = new ReferenceType(ReferenceType.JAVA_CLASS);

  private @Nullable Map<CustomizationKey, Object> myOptions;

  public static final CustomizationKey<Boolean> RESOLVE_QUALIFIED_CLASS_NAME = new CustomizationKey<Boolean>(
    PsiBundle.message("qualified.resolve.class.reference.provider.option")
  );

  public PsiReference[] getReferencesByElement(PsiElement element){
    return getReferencesByElement(element, ourType);
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type){
      String text = element.getText();

      if (element instanceof XmlAttributeValue) {
          final String valueString = ((XmlAttributeValue) element).getValue();
          int startOffset = StringUtil.startsWithChar(text, '"') ||
                            StringUtil.startsWithChar(text, '\'') ? 1 : 0;
          return getReferencesByString(valueString, element, type, startOffset);
      } else if (element instanceof XmlTag) {
        final XmlTagValue value = ((XmlTag)element).getValue();

        if (value != null) {
          text = value.getText();
          final String trimmedText = text.trim();

          return getReferencesByString(
            trimmedText,
            element,
            type,
            value.getTextRange().getStartOffset() + text.indexOf(trimmedText) - element.getTextOffset()
          );
        }
      }

      return getReferencesByString(text, element, type, 0);
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition){
    return new ReferenceSet(str, position, offsetInPosition, type).getAllReferences();
  }

  private static final SoftReference<List<PsiElement>> NULL_REFERENCE = new SoftReference<List<PsiElement>>(null);
  private SoftReference<List<PsiElement>> myDefaultPackageContent = NULL_REFERENCE;
  private Runnable myPackagesEraser = null;

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position){
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    if(position == null) return;
    if(hint == null || hint.shouldProcess(PsiPackage.class) || hint.shouldProcess(PsiClass.class)){
      final List<PsiElement> cachedPackages = getDefaultPackages(position);
      for (final PsiElement psiPackage : cachedPackages) {
        if (!processor.execute(psiPackage, PsiSubstitutor.EMPTY)) return;
      }
    }
  }

  protected List<PsiElement> getDefaultPackages(PsiElement position) {
    List<PsiElement> cachedPackages = myDefaultPackageContent.get();
    if(cachedPackages == null){
      final List<PsiElement> psiPackages = new ArrayList<PsiElement>();
      final PsiManager manager = position.getManager();
      final PsiPackage rootPackage = manager.findPackage("");
      if(rootPackage != null){
        rootPackage.processDeclarations(new BaseScopeProcessor() {
          public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
            psiPackages.add(element);
            return true;
          }
        }, PsiSubstitutor.EMPTY, position, position);
      }
      if(myPackagesEraser == null){
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

  protected class ReferenceSet{
    private final @Nullable Map<CustomizationKey, Object> options = myOptions;
    private JavaReference[] myReferences;
    private PsiElement myElement;
    private final int myStartInElement;
    private final ReferenceType myType;

    ReferenceSet(String str, PsiElement element, int startInElement, ReferenceType type){
      myType = type;
      myStartInElement = startInElement;
      reparse(str,element);
    }

    private void reparse(String str,PsiElement element){
      myElement = element;
      final List<JavaReference> referencesList = new ArrayList<JavaReference>();
      int currentDot = -1;
      int index = 0;
      JavaReference currentContextRef;

      while(true){
        final int nextDot = str.indexOf(SEPARATOR, currentDot + 1);
        final String subreferenceText = nextDot > 0 ? str.substring(currentDot + 1, nextDot) : str.substring(currentDot + 1);
        currentContextRef = new JavaReference(new TextRange(myStartInElement + currentDot + 1,
                                                            myStartInElement + (nextDot > 0 ? nextDot : str.length())),
                                              index++, subreferenceText);
        referencesList.add(currentContextRef);
        if ((currentDot = nextDot) < 0) {
          break;
        }
      }

      myReferences = referencesList.toArray(new JavaReference[referencesList.size()]);
    }

    private void reparse(PsiElement element){
      final String text;
      
      if (element instanceof XmlAttributeValue) {
        text = StringUtil.stripQuotesAroundValue( element.getText() );
      } else if (element instanceof XmlTag) {
        text = ((XmlTag)element).getValue().getTrimmedText();
      } else {
        text = element.getText();
      }
      reparse(text,element);
    }

    private JavaReference getReference(int index){
      return myReferences[index];
    }

    protected JavaReference[] getAllReferences(){
      return myReferences;
    }

    private ReferenceType getType(int index){
      if(index != myReferences.length - 1){
        return new ReferenceType(ReferenceType.JAVA_CLASS, ReferenceType.JAVA_PACKAGE);
      }
      return myType;
    }

    public class JavaReference extends GenericReference implements PsiJavaReference {
      private final int myIndex;
      private TextRange myRange;
      private final String myText;

      public JavaReference(TextRange range, int index, String text){
        super(JavaClassReferenceProvider.this);
        LOG.assertTrue(range.getEndOffset() <= myElement.getTextLength());
        myIndex = index;
        myRange = range;
        myText = text;
      }

      public PsiElement getContext(){
        final PsiReference contextRef = getContextReference();
        return contextRef != null ? contextRef.resolve() : null;
      }

      public PsiReference getContextReference(){
        return myIndex > 0 ? getReference(myIndex - 1) : null;
      }

      public ReferenceType getType(){
        return ReferenceSet.this.getType(myIndex);
      }

      public ReferenceType getSoftenType(){
        return new ReferenceType(ReferenceType.JAVA_CLASS, ReferenceType.JAVA_PACKAGE);
      }

      public boolean needToCheckAccessibility() {
        return false;
      }

      public PsiElement getElement(){
        return myElement;
      }

      public boolean isReferenceTo(PsiElement element) {
        if (element instanceof PsiClass || element instanceof PsiPackage) {
          return super.isReferenceTo(element);
        }
        return false;
      }

      public TextRange getRangeInElement(){
        return myRange;
      }

      public String getCanonicalText(){
        return myText;
      }

      public boolean isSoft(){
        return ReferenceSet.this.isSoft();
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException{
        final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
        if(manipulator != null){
          final PsiElement element = manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName);
          myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
          return element;
        }
        throw new IncorrectOperationException("Manipulator for this element is not defined");
      }

      public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException{
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
        final TextRange range = new TextRange(getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
        final PsiElement finalElement = getManipulator(getElement()).handleContentChange(getElement(), range, newName);
        reparse(finalElement);
        return finalElement;
      }

      public PsiElement resolveInner() {
        return advancedResolve(true).getElement();
      }

      public Object[] getVariants() {
        final PsiElement context = getContext();
        if (context != null) {
          return processPackage((PsiPackage)context);
        } else {
          final PsiPackage defaultPackage = myElement.getManager().findPackage("");
          if (defaultPackage != null) {
            return processPackage(defaultPackage);
          }
          return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
      }

      private Object[] processPackage(final PsiPackage aPackage) {
        final PsiPackage[] subPackages = aPackage.getSubPackages();
        final PsiClass[] classes = aPackage.getClasses();
        Object[] result = new Object[subPackages.length + classes.length];
        System.arraycopy(subPackages, 0, result, 0, subPackages.length);
        System.arraycopy(classes, 0, result, subPackages.length, classes.length);
        return result;
      }

      @NotNull
      public JavaResolveResult advancedResolve(boolean incompleteCode) {
        if (!myElement.isValid()) return JavaResolveResult.EMPTY;
        String qName = getElement().getText().substring(getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());

        if (myIndex == myReferences.length - 1) {
          final PsiClass aClass = myElement.getManager().findClass(qName, GlobalSearchScope.allScope(myElement.getProject()));
          if (aClass != null) {
            return new ClassCandidateInfo(aClass, PsiSubstitutor.EMPTY, false, false, myElement);
          }
        }
        PsiElement resolveResult = myElement.getManager().findPackage(qName);
        if(resolveResult == null)
          resolveResult = myElement.getManager().findClass(qName, GlobalSearchScope.allScope(myElement.getProject()));

        if(resolveResult == null){
          final PsiFile containingFile = myElement.getContainingFile();
          if(containingFile instanceof PsiJavaFile) {
            final ClassResolverProcessor processor = new ClassResolverProcessor(getCanonicalText(), myElement);
            containingFile.processDeclarations(processor, PsiSubstitutor.EMPTY, null, myElement);
            if(processor.getResult().length == 1) {
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
        return resolveResult != null ?
               new ClassCandidateInfo(resolveResult, PsiSubstitutor.EMPTY, false, false, myElement):
               JavaResolveResult.EMPTY;
      }

      @NotNull
      public JavaResolveResult[] multiResolve(boolean incompleteCode) {
        final JavaResolveResult javaResolveResult = advancedResolve(incompleteCode);
        if(javaResolveResult.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
        return new JavaResolveResult[]{javaResolveResult};
      }
    }

    protected boolean isSoft(){
      return false;
    }
  }
}
