package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
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
public class FileReference
  implements PsiPolyVariantReference, QuickFixProvider<FileReference>, LocalQuickFixProvider, EmptyResolveMessageProvider {
  public static final FileReference[] EMPTY = new FileReference[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference");

  private final int myIndex;
  private TextRange myRange;
  private final String myText;
  @NotNull private final FileReferenceSet myFileReferenceSet;
  private ResolveResult[] myCachedResult;

  public FileReference(final @NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    myFileReferenceSet = fileReferenceSet;
    myIndex = index;
    myRange = range;
    myText = text;
  }

  @NotNull
  private Collection<PsiFileSystemItem> getContexts() {
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

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    if (myCachedResult == null) {
      myCachedResult = innerResolve();
    }
    return myCachedResult;
  }

  protected ResolveResult[] innerResolve() {
    final String text = getText();
    final Collection<PsiFileSystemItem> contexts = getContexts();
    final Collection<ResolveResult> result = new ArrayList<ResolveResult>(contexts.size());

    for (final PsiFileSystemItem context : contexts) {
      if (text.length() == 0 && !myFileReferenceSet.isEndingSlashNotAllowed() && isLast() || ".".equals(text) || "/".equals(text)) {
        result.add(new PsiElementResolveResult(context));
      } else if ("..".equals(text)) {
        final PsiFileSystemItem resolved = context.getParent();
        if (resolved != null) {
          result.add(new PsiElementResolveResult(resolved));
        }
      } else {
        processVariants(context, new BaseScopeProcessor() {
          public boolean execute(final PsiElement element, final PsiSubstitutor substitutor) {
            final String name = ((PsiFileSystemItem)element).getName();
            if (myFileReferenceSet.isCaseSensitive() ? text.equals(name) : text.compareToIgnoreCase(name) == 0) {
              result.add(new PsiElementResolveResult(element));
              return false;
            }
            return true;
          }
        });
      }
    }
    final int resultCount = result.size();
    return resultCount > 0 ? result.toArray(new ResolveResult[resultCount]) : ResolveResult.EMPTY_ARRAY;
  }

  public Object[] getVariants() {
    final String s = getText();
    if (s != null && s.equals("/")) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    try {
      final List ret = new ArrayList();
      final List<Class> allowedClasses = new ArrayList<Class>();
      allowedClasses.add(PsiFile.class);
      for (final FileReferenceHelper helper : getHelpers()) {
        allowedClasses.add(helper.getDirectoryClass());
      }
      final PsiScopeProcessor proc =
        myFileReferenceSet.createProcessor(ret, allowedClasses, Arrays.<PsiConflictResolver>asList(new DuplicateConflictResolver()));
      for (PsiFileSystemItem context : getContexts()) {
        processVariants(context, proc);
      }
      return ret.toArray();
    }
    catch (ProcessorRegistry.IncompatibleReferenceTypeException e) {
      LOG.error(e);
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  private void processVariants(final PsiFileSystemItem context, final PsiScopeProcessor processor) {
    for (PsiElement element : context.getChildren()) {
      if (element instanceof PsiFileSystemItem) {
        PsiFileSystemItem item = (PsiFileSystemItem)element;
        final VirtualFile file = item.getVirtualFile();
        if (file != null && !file.isDirectory()) {
          final PsiFile psiFile = getElement().getManager().findFile(file);
          if (psiFile != null) {
            item = psiFile;
          }
        }
        if (!processor.execute(item, PsiSubstitutor.EMPTY)) break;
      }
    }
  }

  @Nullable
  private FileReference getContextReference() {
    return myIndex > 0 ? myFileReferenceSet.getReference(myIndex - 1) : null;
  }

  public PsiElement getElement() {
    return myFileReferenceSet.getElement();
  }

  public PsiFileSystemItem resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiFileSystemItem)) return false;

    final PsiFileSystemItem item = resolve();
    return item != null && FileReferenceHelperRegistrar.areElementsEquivalent(item, (PsiFileSystemItem)element);
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

  public boolean isSoft() {
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

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element, should be instanceof PsiFileSystemItem: " + element);

    final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)element;
    VirtualFile dstVFile = fileSystemItem.getVirtualFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

    final PsiFile file = getElement().getContainingFile();
    final VirtualFile curVFile = file.getVirtualFile();
    if (curVFile == null) throw new IncorrectOperationException("Cannot bind from non-physical element:" + file);

    final Project project = element.getProject();

    final String newName;
    if (myFileReferenceSet.isAbsolutePathReference()) {
      PsiFileSystemItem root = null;
      PsiFileSystemItem dstItem = null;
      for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
        PsiFileSystemItem _dstItem = helper.getPsiFileSystemItem(project, dstVFile);
        if (_dstItem != null) {
          PsiFileSystemItem _root = helper.findRoot(project, dstVFile);
          if (_root != null) {
            root = _root;
            dstItem = _dstItem;
            break;
          }
        }
      }
      if (root == null) return element;

      newName = "/" + PsiFileSystemItemUtil.getNotNullRelativePath(root, dstItem);
    } else {
      PsiFileSystemItem curItem = null;
      PsiFileSystemItem dstItem = null;
      for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
        PsiFileSystemItem _curItem = helper.getPsiFileSystemItem(project, curVFile);
        if (_curItem != null) {
          PsiFileSystemItem _dstItem = helper.getPsiFileSystemItem(project, dstVFile);
          if (_dstItem != null) {
            curItem = _curItem;
            dstItem = _dstItem;
            break;
          }
        }
      }
      checkNotNull(curItem, curVFile, dstVFile);
      newName = PsiFileSystemItemUtil.getNotNullRelativePath(curItem, dstItem);
    }

    final TextRange range = new TextRange(myFileReferenceSet.getStartInElement(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = GenericReference.getManipulator(getElement());
    if (manipulator == null) {
      throw new IncorrectOperationException("Manipulator not defined for: " + getElement());
    }
    return manipulator.handleContentChange(getElement(), range, newName);
  }

  private static void checkNotNull(final Object o, final VirtualFile curVFile, final VirtualFile dstVFile) throws IncorrectOperationException {
    if (o == null) {
      throw new IncorrectOperationException("Cannot find path between files; src = " + curVFile.getPresentableUrl() + "; dst = " + dstVFile.getPresentableUrl());
    }
  }

  public void registerQuickfix(HighlightInfo info, FileReference reference) {
    for (final FileReferenceHelper helper : getHelpers()) {
      helper.registerFixes(info, reference);
    }
  }

  protected List<FileReferenceHelper> getHelpers() {
    return FileReferenceHelperRegistrar.getHelpers();
  }

  public int getIndex() {
    return myIndex;
  }

  public String getUnresolvedMessagePattern() {
    final StringBuffer builder = new StringBuffer(JavaErrorMessages.message("error.cannot.resolve"));
    builder.append(" ").append(myFileReferenceSet.getTypeName());
    if (!isLast()) {
      for (final FileReferenceHelper helper : getHelpers()) {
        builder.append(" ").append(JavaErrorMessages.message("error.cannot.resolve.infix")).append(" ")
          .append(helper.getDirectoryTypeName());
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
    myCachedResult = null;
  }

  public LocalQuickFix[] getQuickFixes() {
    final List<LocalQuickFix> result = new ArrayList<LocalQuickFix>();
    for (final FileReferenceHelper<?> helper : getHelpers()) {
      result.addAll(helper.registerFixes(null, this));
    }
    return result.toArray(new LocalQuickFix[result.size()]);
  }
}
