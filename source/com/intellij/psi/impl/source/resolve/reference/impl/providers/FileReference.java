package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
  */
 public class FileReference implements PsiPolyVariantReference, QuickFixProvider<FileReference>,
                                                                EmptyResolveMessageProvider {
   public static final FileReference[] EMPTY = new FileReference[0];
   private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference");

   private final int myIndex;
   private TextRange myRange;
   private final String myText;
   @NotNull private final FileReferenceSet myFileReferenceSet;
   private final Condition<String> myEqualsToCondition = new Condition<String>() {
     public boolean value(String s) {
       return equalsTo(s);
     }
   };

   public FileReference(final @NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text){
     myFileReferenceSet = fileReferenceSet;
     myIndex = index;
     myRange = range;
     myText = text;
   }

   @NotNull private Collection<PsiFileSystemItem> getContexts(){
     final FileReference contextRef = getContextReference();
     if (contextRef == null) {
       return myFileReferenceSet.getDefaultContexts(myFileReferenceSet.getElement());
     }
     ResolveResult[] resolveResults = contextRef.multiResolve(false);
     ArrayList<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
     for (ResolveResult resolveResult : resolveResults) {
       result.add((PsiFileSystemItem)resolveResult.getElement());
     }
     return result;
   }

   @Nullable
   public static PsiDirectory getPsiDirectory(PsiFileSystemItem element) {
     final FileReferenceHelper<PsiFileSystemItem> helper = FileReferenceHelperRegistrar.getHelper(element);
     return helper != null ? helper.getPsiDirectory(element) : null;
   }

   @Nullable
   public PsiFileSystemItem getContext() {
     final FileReference contextRef = getContextReference();
     return contextRef != null ? contextRef.resolve() : null;
   }

   @NotNull
   public ResolveResult[] multiResolve(final boolean incompleteCode) {
     final PsiManager manager = getElement().getManager();
     if (manager instanceof PsiManagerImpl) {
       return ((PsiManagerImpl) manager).getResolveCache().resolveWithCaching(
         this, MyResolver.INSTANCE, false, false
       );
     }
     return innerResolve();
   }

   protected ResolveResult[] innerResolve() {
     final String text = getText();
     final Collection<PsiFileSystemItem> contexts = getContexts();
     final Collection<ResolveResult> result = new ArrayList<ResolveResult>(contexts.size());

     for (final PsiFileSystemItem context : contexts) {
     if (text.length() == 0 && !myFileReferenceSet.isEndingSlashNotAllowed() && isLast()) {
         result.add(new PsiElementResolveResult(context));
       } else {
       final FileReferenceHelper<PsiFileSystemItem> helper = FileReferenceHelperRegistrar.getHelper(context);
       if (helper != null) {
         PsiFileSystemItem resolved = helper.innerResolve(context, text, myEqualsToCondition);
         if (resolved != null) {
           result.add(new PsiElementResolveResult(resolved));
         }
       }
       }
     }
     final int resultCount = result.size();
     return resultCount > 0 ? result.toArray(new ResolveResult[resultCount]) : ResolveResult.EMPTY_ARRAY;
   }

   public Object[] getVariants(){
     final String s = getText();
     if (s != null && s.equals("/")) {
       return ArrayUtil.EMPTY_OBJECT_ARRAY;
     }
     try{
       final List ret = new ArrayList();
       final List<Class> allowedClasses = new ArrayList<Class>();
       allowedClasses.add(PsiFile.class);
       for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
         allowedClasses.add(helper.getDirectoryClass());
       }
       final PsiScopeProcessor proc = myFileReferenceSet.createProcessor(ret, allowedClasses, Arrays.<PsiConflictResolver>asList(new DuplicateConflictResolver()));
       processVariants(proc);
       return ret.toArray();
     }
     catch(ProcessorRegistry.IncompatibleReferenceTypeException e){
       LOG.error(e);
       return ArrayUtil.EMPTY_OBJECT_ARRAY;
     }
   }

   public void processVariants(@NotNull final PsiScopeProcessor processor) {
     for (PsiFileSystemItem context : getContexts()) {
       final FileReferenceHelper<PsiFileSystemItem> helper = FileReferenceHelperRegistrar.getHelper(context);
       if (helper != null && !helper.processVariants(context, processor)) return;
     }
   }

   @Nullable
   public FileReference getContextReference(){
     return myIndex > 0 ? myFileReferenceSet.getReference(myIndex - 1) : null;
   }

   public PsiElement getElement(){
     return myFileReferenceSet.getElement();
   }

   public PsiFileSystemItem resolve() {
     ResolveResult[] resolveResults = multiResolve(false);
     return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
   }

   private boolean equalsTo(final String name) {
     return myFileReferenceSet.isCaseSensitive() ? getText().equals(name) :
            getText().compareToIgnoreCase(name) == 0;
   }

   public boolean isReferenceTo(PsiElement element) {
     if (!(element instanceof PsiFileSystemItem)) return false;

     final PsiFileSystemItem resolveResult = resolve();
     final PsiManager manager = element.getManager();
     return element instanceof PsiDirectory && manager.areElementsEquivalent(element, getPsiDirectory(resolveResult)) ||
            manager.areElementsEquivalent(element, resolveResult);
   }

   public TextRange getRangeInElement() {
     return myRange;
   }

   public String getCanonicalText() {
     return myText;
   }

   protected String getText() {
     return myText;
   }

   public boolean isSoft(){
     return myFileReferenceSet.isSoft();
   }

   public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
     final ElementManipulator<PsiElement> manipulator = GenericReference.getManipulator(getElement());
     if (manipulator != null) {
       myFileReferenceSet.setElement(manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName));
       //Correct ranges
       int delta = newElementName.length() - myRange.getLength();
       myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
       FileReference[] references = myFileReferenceSet.getAllReferences();
       for (int idx = myIndex + 1; idx < references.length; idx++) {
         references[idx].myRange = references[idx].myRange.shiftRight(delta);
       }
       return myFileReferenceSet.getElement();
     }
     throw new IncorrectOperationException("Manipulator for this element is not defined");
   }

   public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException{
     if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element");
     PsiFileSystemItem fileSystemItem = (PsiFileSystemItem) element;
     VirtualFile dstVFile = PsiUtil.getVirtualFile(fileSystemItem);

     final PsiFile file = getElement().getContainingFile();
     if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

     for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
       if (helper.isDoNothingOnBind(file, this)) {
         return element;
       }
     }

     final VirtualFile currentFile = file.getVirtualFile();
     LOG.assertTrue(currentFile != null);

     String newName = null;
     for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
       final String s = helper.getRelativePath(file.getProject(), currentFile, dstVFile);
       if (s != null) {
         newName = s;
         break;
       }
     }

     if (newName == null) {
       throw new IncorrectOperationException("Cannot find path between files; src = " +
                                             currentFile.getPresentableUrl() + "; dst = " + dstVFile.getPresentableUrl());
     }

     final TextRange range = new TextRange(myFileReferenceSet.getStartInElement(), getRangeInElement().getEndOffset());
     final ElementManipulator<PsiElement> manipulator = GenericReference.getManipulator(getElement());
     if (manipulator == null) {
       throw new IncorrectOperationException("Manipulator not defined for: " + getElement());
     }
     return manipulator.handleContentChange(getElement(), range, newName);
   }

   public void registerQuickfix(HighlightInfo info, FileReference reference) {
     for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
       helper.registerQuickfix(info, reference);
     }
   }

   public int getIndex() {
     return myIndex;
   }

   public String getUnresolvedMessagePattern(){
     final StringBuffer builder = new StringBuffer(JavaErrorMessages.message("error.cannot.resolve"));
     builder.append(" ").append(myFileReferenceSet.getTypeName());
     if (!isLast()) {
       for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
         builder.append(" ").append(JavaErrorMessages.message("error.cannot.resolve.infix")).append(" ").append(helper.getDirectoryTypeName());
       }
     }
     builder.append(" ''{0}''.");
     return builder.toString();
   }

   public final boolean isLast() {
     return myIndex == myFileReferenceSet.getAllReferences().length - 1;
   }

   @NotNull
   public FileReferenceSet getFileReferenceSet() {
     return myFileReferenceSet;
   }

   public void clearResolveCaches() {
     final PsiManager manager = getElement().getManager();
     if (manager instanceof PsiManagerImpl) {
       ((PsiManagerImpl)manager).getResolveCache().clearResolveCaches(this);
     }
   }

   static class MyResolver implements ResolveCache.PolyVariantResolver {
         static MyResolver INSTANCE = new MyResolver();
         public ResolveResult[] resolve(PsiPolyVariantReference ref, boolean incompleteCode) {
                 return ((FileReference)ref).innerResolve();
               }
             }
           }
