package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.jsp.CompositeLanguageParsingUtil;
import com.intellij.psi.impl.source.jsp.JspImplUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspWhileStatement;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;

import java.lang.ref.WeakReference;
import java.util.*;

public class CompositeLanguageFileViewProvider extends SingleRootFileViewProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.CompositeLanguageFileViewProvider");
  private final Map<Language, PsiFile> myRoots = new HashMap<Language, PsiFile>();
  private final Map<PsiFile, Set<WeakReference<OuterLanguageElement>>> myOuterLanguageElements =
    new HashMap<PsiFile, Set<WeakReference<OuterLanguageElement>>>();
  private Set<PsiFile> myRootsInUpdate = new HashSet<PsiFile>(4);

  public CompositeLanguageFileViewProvider(final PsiManager manager, final VirtualFile file) {
    super(manager, file);
  }

  public CompositeLanguageFileViewProvider(final PsiManager manager, final VirtualFile virtualFile, final boolean physical) {
    super(manager, virtualFile, physical);
  }

  public CompositeLanguageFileViewProvider clone() {
    final CompositeLanguageFileViewProvider viewProvider = cloneInner();
    final PsiFileImpl psiFile = (PsiFileImpl)viewProvider.getPsi(getBaseLanguage());

    // copying main tree
    final FileElement treeClone = (FileElement)psiFile.calcTreeElement().clone(); // base language tree clone
    psiFile.setTreeElementPointer(treeClone); // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    treeClone.setPsiElement(psiFile);

    final XmlText[] xmlTexts = JspImplUtil.gatherTexts((XmlFile)psiFile);
    for (Map.Entry<Language, PsiFile> entry : myRoots.entrySet()) {
      final PsiFile root = entry.getValue();
      if (root instanceof PsiFileImpl && root != psiFile) {
        final PsiFileImpl copy = (PsiFileImpl)viewProvider.getPsi(entry.getKey());
        if (copy == null) continue; // Unreleivant language due to partial parsing.
        JspImplUtil.copyRoot((PsiFileImpl)root, xmlTexts, copy);
      }
    }
    return viewProvider;
  }

  protected CompositeLanguageFileViewProvider cloneInner() {
    final CompositeLanguageFileViewProvider viewProvider = new CompositeLanguageFileViewProvider(getManager(), new LightVirtualFile(
      getVirtualFile().getName(), getVirtualFile().getFileType(), getContents(), getModificationStamp()), false);
    return viewProvider;
  }

  protected PsiFile getPsiInner(Language target) {
    PsiFile file = super.getPsiInner(target);
    if (file != null) return file;
    file = myRoots.get(target);
    if (file == null) {
      synchronized (PsiLock.LOCK) {
        file = createFile(target);
        myRoots.put(target, file);
      }
    }
    return file;
  }

  public synchronized PsiFile getCachedPsi(Language target) {
    if (target == getBaseLanguage()) return super.getCachedPsi(target);
    return myRoots.get(target);
  }

  public void checkAllTreesEqual() {
    final String psiText = getPsi(getBaseLanguage()).getText();
    for (Map.Entry<Language, PsiFile> entry : myRoots.entrySet()) {
      final PsiFile psiFile = entry.getValue();
      LOG.assertTrue(psiFile.getTextLength() == psiText.length(), entry.getKey().getID() + " tree text differs from base!");
      LOG.assertTrue(psiFile.getText().equals(psiText), entry.getKey().getID() + " tree text differs from base!");
    }
  }

  public void registerOuterLanguageElement(OuterLanguageElement element, PsiFile root) {
    Set<WeakReference<OuterLanguageElement>> outerLanguageElements = myOuterLanguageElements.get(root);
    if (outerLanguageElements == null) {
      outerLanguageElements = new TreeSet<WeakReference<OuterLanguageElement>>(new Comparator<WeakReference<OuterLanguageElement>>() {
        public int compare(final WeakReference<OuterLanguageElement> o1, final WeakReference<OuterLanguageElement> o2) {
          final OuterLanguageElement languageElement1 = o1.get();
          final OuterLanguageElement languageElement2 = o2.get();
          if (languageElement1 == null && languageElement2 == null) return 0;
          if (languageElement1 == null) return -1;
          if (languageElement1.equals(languageElement2)) return 0;
          if (languageElement2 == null) return 1;
          final int result = languageElement1.getTextRange().getStartOffset() - languageElement2.getTextRange().getStartOffset();
          return result != 0 ? result : 1;
        }
      });
      myOuterLanguageElements.put(root, outerLanguageElements);
    }
    outerLanguageElements.add(new WeakReference<OuterLanguageElement>(element));
  }

  public void reparseRoots(final Set<Language> rootsToReparse) {
    for (final Language lang : rootsToReparse) {
      final PsiFile cachedRoot = myRoots.get(lang);
      if (cachedRoot != null) {
        if (myRootsInUpdate.contains(cachedRoot)) continue;
        try {
          myRootsInUpdate.add(cachedRoot);
          reparseRoot(lang, cachedRoot);
        }
        finally {
          myRootsInUpdate.remove(cachedRoot);
        }
      }
    }
  }


  public FileElement[] getKnownTreeRoots() {
    final List<FileElement> knownRoots = new ArrayList<FileElement>();
    knownRoots.addAll(Arrays.asList(super.getKnownTreeRoots()));
    for (PsiFile psiFile : myRoots.values()) {
      if (psiFile == null || !(psiFile instanceof PsiFileImpl)) continue;
      final FileElement fileElement = ((PsiFileImpl)psiFile).getTreeElement();
      if (fileElement == null) continue;
      knownRoots.add(fileElement);
    }
    return knownRoots.toArray(new FileElement[knownRoots.size()]);
  }

  protected void reparseRoot(final Language lang, final PsiFile cachedRoot) {
    LOG.debug("JspxFile: reparseRoot " + getVirtualFile().getName());
    final PsiFile psiFileImpl = cachedRoot;
    final ASTNode oldFileTree = psiFileImpl.getNode();
    if (oldFileTree == null || oldFileTree.getFirstChildNode()instanceof ChameleonElement) {
      if (psiFileImpl instanceof PsiFileImpl) ((PsiFileImpl)psiFileImpl).setTreeElementPointer(null);
      psiFileImpl.subtreeChanged();
      return;
    }
    final PsiFile fileForNewText = createFile(lang);
    final ASTNode newFileTree = fileForNewText.getNode();
    ChameleonTransforming.transformChildren(newFileTree, true);
    ChameleonTransforming.transformChildren(oldFileTree, true);
    CompositeLanguageParsingUtil.mergeTreeElements((TreeElement)newFileTree.getFirstChildNode(),
                                                   (TreeElement)oldFileTree.getFirstChildNode(), (CompositeElement)oldFileTree);
    checkConsistensy(cachedRoot);
  }

  public void updateOuterLanguageElements(final Set<Language> reparsedRoots) {
    for (Map.Entry<Language, PsiFile> entry : myRoots.entrySet()) {
      final PsiFile psiFile = entry.getValue();
      final Language updatedLanguage = entry.getKey();
      if (reparsedRoots.contains(updatedLanguage)) continue;
      final Set<WeakReference<OuterLanguageElement>> list = myOuterLanguageElements.get(psiFile);
      if (list == null) // not parsed yet
      {
        continue;
      }
      try {
        myRootsInUpdate.add(psiFile);
        final Iterator<WeakReference<OuterLanguageElement>> iterator = list.iterator();
        XmlText prevText = null;
        while (iterator.hasNext()) {
          WeakReference<OuterLanguageElement> reference = iterator.next();
          final OuterLanguageElement outerElement = reference.get();
          if (outerElement == null) {
            iterator.remove();
            continue;
          }
          final FileElement file = TreeUtil.getFileElement(outerElement);
          if (file == null || file.getPsi() != psiFile) {
            iterator.remove();
            continue;
          }
          final XmlText nextText = outerElement.getFollowingText();
          final TextRange textRange = new TextRange(prevText != null ? prevText.getTextRange().getEndOffset() : 0,
                                                    nextText != null ? nextText.getTextRange().getStartOffset() : getContents().length());
          if (!textRange.equals(outerElement.getTextRange())) {
            outerElement.setRange(textRange);
          }
          prevText = nextText;
        }
      }
      finally {
        myRootsInUpdate.remove(psiFile);
        checkConsistensy(psiFile);
      }
    }
  }

  public PsiElement findElementAt(int offset, Class<? extends Language> lang) {
    final PsiFile mainRoot = getPsi(getBaseLanguage());
    PsiElement ret = null;
    for (final Language language : getRelevantLanguages()) {
      if (!lang.isAssignableFrom(language.getClass())) continue;

      final PsiFile psiRoot = getPsi(language);
      final PsiElement psiElement;
      psiElement = findElementAt(psiRoot, offset);
      if (psiElement == null || psiElement instanceof OuterLanguageElement) continue;
      if (ret == null || psiRoot != mainRoot) {
        ret = psiElement;
      }
    }
    return ret;
  }

  public PsiElement findElementAt(int offset) {
    return findElementAt(offset, Language.class);
  }

  public PsiReference findReferenceAt(int offset) {
    TextRange minRange = new TextRange(0, getContents().length());
    PsiReference ret = null;
    for (final Language language : getRelevantLanguages()) {
      final PsiElement psiRoot = getPsi(language);
      final PsiReference reference = SharedPsiElementImplUtil.findReferenceAt(psiRoot, offset);
      if (reference == null) continue;
      final TextRange textRange = reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset());
      if (minRange.contains(textRange)) {
        minRange = textRange;
        ret = reference;
      }
    }
    return ret;
  }

  protected void checkConsistensy(final PsiFile oldFile) {
    LOG.assertTrue(oldFile.getNode().getTextLength() == getContents().length());
    LOG.assertTrue(oldFile.getNode().getText().equals(getContents().toString()));
  }

  public LanguageExtension[] getLanguageExtensions() {
    return new LanguageExtension[0];
  }

  protected PsiFile createFile(Language lang) {
    final PsiFile psiFile = super.createFile(lang);
    if (psiFile != null) return psiFile;
    if (getRelevantLanguages().contains(lang)) return lang.getParserDefinition().createFile(this);
    return null;
  }

  public void rootChanged(PsiFile psiFile) {
    if (myRootsInUpdate.contains(psiFile)) return;
    if (psiFile.getLanguage() == getBaseLanguage()) {
      super.rootChanged(psiFile);
      // rest of sync mechanism is now handeled by JspxAspect
    }
    else if (!myRootsInUpdate.contains(getPsi(getBaseLanguage()))) doHolderToXmlChanges(psiFile);
  }

  private void doHolderToXmlChanges(final PsiFile psiFile) {
    final Language language = getBaseLanguage();
    final List<Pair<OuterLanguageElement, Pair<StringBuffer, StringBuffer>>> javaFragments =
      new ArrayList<Pair<OuterLanguageElement, Pair<StringBuffer, StringBuffer>>>();
    try {
      StringBuffer currentBuffer = null;
      StringBuffer currentDecodedBuffer = null;
      LeafElement element = TreeUtil.findFirstLeaf(psiFile.getNode());
      if (element == null) return;
      do {
        if (element instanceof OuterLanguageElement) {
          javaFragments.add(Pair.create((OuterLanguageElement)element,
                                        Pair.create(currentBuffer = new StringBuffer(), currentDecodedBuffer = new StringBuffer())));
        }
        else {
          final String text = element.getText();
          final String decoded = language != StdLanguages.JSP ? XmlUtil.decode(text) : text;
          currentDecodedBuffer.append(decoded);
          currentBuffer.append(text);
        }
      }
      while ((element = ParseUtil.nextLeaf(element, null)) != null);

      for (final Pair<OuterLanguageElement, Pair<StringBuffer, StringBuffer>> pair : javaFragments) {
        final XmlText followingText = pair.getFirst().getFollowingText();
        final String buffer = pair.getSecond().getFirst().toString();
        if (followingText != null && followingText.isValid() && !followingText.getText().equals(buffer)) {
          followingText.setValue(pair.getSecond().getSecond().toString());
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void normalizeOuterLanguageElements(PsiFile root) {
    if (!myRootsInUpdate.contains(root)) {
      try {
        myRootsInUpdate.add(root);
        normalizeOuterLanguageElementsInner(root);
      }
      finally {
        myRootsInUpdate.remove(root);
      }
    }
    else {
      normalizeOuterLanguageElementsInner(root);
    }
  }

  private void normalizeOuterLanguageElementsInner(final PsiFile lang) {
    final Set<WeakReference<OuterLanguageElement>> outerElements = myOuterLanguageElements.get(lang);
    if (outerElements == null) return;
    final Iterator<WeakReference<OuterLanguageElement>> iterator = outerElements.iterator();
    OuterLanguageElement prev = null;
    while (iterator.hasNext()) {
      final WeakReference<OuterLanguageElement> outerElement = iterator.next();

      final OuterLanguageElement outer = outerElement.get();
      if (outer == null) {
        iterator.remove();
        continue;
      }

      if (prev != null && prev.getFollowingText() == null && outer.getTextRange().getStartOffset() == prev.getTextRange().getEndOffset()) {
        final CompositeElement prevParent = prev.getTreeParent();
        if (prevParent != null && prevParent.getElementType() == JspElementType.JSP_TEMPLATE_EXPRESSION ||
            PsiTreeUtil.getParentOfType(outer, JspWhileStatement.class) != null) {
          prev = mergeOuterLanguageElements(outer, prev);
        }
        else {
          prev = mergeOuterLanguageElements(prev, outer);
        }
      }
      else {
        prev = outer;
      }
    }
  }

  private static OuterLanguageElement mergeOuterLanguageElements(final OuterLanguageElement prev, final OuterLanguageElement outer) {
    final TextRange textRange = new TextRange(Math.min(prev.getTextRange().getStartOffset(), outer.getTextRange().getStartOffset()),
                                              Math.max(prev.getTextRange().getEndOffset(), outer.getTextRange().getEndOffset()));
    prev.setRange(textRange);
    if (prev.getFollowingText() == null) prev.setFollowingText(outer.getFollowingText());
    final CompositeElement parent = prev.getTreeParent();
    if (parent != null) parent.subtreeChanged();
    final CompositeElement removedParent = outer.getTreeParent();
    TreeUtil.remove(outer);
    if (removedParent != null) removedParent.subtreeChanged();
    return prev;
  }
}
