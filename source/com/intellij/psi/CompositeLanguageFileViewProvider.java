package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.jsp.CompositeLanguageParsingUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.xml.XmlText;

import java.util.*;
import java.lang.ref.WeakReference;

public class CompositeLanguageFileViewProvider extends SingleRootFileViewProvider{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.CompositeLanguageFileViewProvider");
  protected final Map<Language, PsiFile> myRoots = new HashMap<Language, PsiFile>();
  protected final Map<PsiFile, Set<WeakReference<OuterLanguageElement>>> myOuterLanguageElements =
    new HashMap<PsiFile, Set<WeakReference<OuterLanguageElement>>>();
  protected Set<PsiFile> myRootsInUpdate = new HashSet<PsiFile>(4);

  public CompositeLanguageFileViewProvider(final PsiManager manager, final VirtualFile file) {
    super(manager, file);
  }

  public CompositeLanguageFileViewProvider(final PsiManager manager, final VirtualFile virtualFile, final boolean physical) {
    super(manager, virtualFile, physical);
  }

  protected PsiFile getPsiInner(Language target) {
    if(target == StdLanguages.XHTML && getBaseLanguage() == StdLanguages.JSPX)
      target = getBaseLanguage();
    PsiFile file = super.getPsiInner(target);
    if(file != null) return file;
    file = myRoots.get(target);
    if(file == null) {
      synchronized(PsiLock.LOCK){
        file = createFile(target);
        myRoots.put(target, file);
      }
    }
    return file;
  }

  public synchronized PsiFile getCachedPsi(Language target) {
    if(target == getBaseLanguage()) return super.getCachedPsi(target);
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

  public void registerOuterLanguageElement(OuterLanguageElement element, PsiFile root){
    Set<WeakReference<OuterLanguageElement>> outerLanguageElements = myOuterLanguageElements.get(root);
    if(outerLanguageElements == null){
      outerLanguageElements = new TreeSet<WeakReference<OuterLanguageElement>>(new Comparator<WeakReference<OuterLanguageElement>>() {
        public int compare(final WeakReference<OuterLanguageElement> o1, final WeakReference<OuterLanguageElement> o2) {
          final OuterLanguageElement languageElement1 = o1.get();
          final OuterLanguageElement languageElement2 = o2.get();
          if(languageElement1 == null && languageElement2 == null) return 0;
          if(languageElement1 == null) return -1;
          if(languageElement1.equals(languageElement2)) return 0;
          if(languageElement2 == null) return 1;
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
      if(cachedRoot != null){
        if(myRootsInUpdate.contains(cachedRoot)) continue;
        try {
          myRootsInUpdate.add(cachedRoot);
          reparseRoot(lang, cachedRoot);
        }
        finally{
          myRootsInUpdate.remove(cachedRoot);
        }
      }
    }
  }

  protected void reparseRoot(final Language lang, final PsiFile cachedRoot) {
    LOG.debug("JspxFile: reparseRoot "+getVirtualFile().getName());
    final PsiFileImpl psiFileImpl = ((PsiFileImpl)cachedRoot);
    final ASTNode oldFileTree = psiFileImpl.getTreeElement();
    if(oldFileTree == null || oldFileTree.getFirstChildNode() instanceof ChameleonElement){
      psiFileImpl.setTreeElementPointer(null);
      psiFileImpl.subtreeChanged();
      return;
    }
    final PsiFile fileForNewText = createFile(lang);
    final ASTNode newFileTree = fileForNewText.getNode();
    ChameleonTransforming.transformChildren(newFileTree, true);
    ChameleonTransforming.transformChildren(oldFileTree, true);
    CompositeLanguageParsingUtil.mergeTreeElements((TreeElement)newFileTree.getFirstChildNode(), (TreeElement)oldFileTree.getFirstChildNode(), (CompositeElement)oldFileTree);
    checkConsistensy(cachedRoot);
  }

  public void updateOuterLanguageElements(final Set<Language> reparsedRoots) {
    for (Map.Entry<Language, PsiFile> entry : myRoots.entrySet()) {
      final PsiFile psiFile = entry.getValue();
      final Language updatedLanguage = entry.getKey();
      if(reparsedRoots.contains(updatedLanguage)) continue;
      final Set<WeakReference<OuterLanguageElement>> list = myOuterLanguageElements.get(psiFile);
      if(list == null) // not parsed yet
        continue;
      try{
        myRootsInUpdate.add(psiFile);
        final Iterator<WeakReference<OuterLanguageElement>> iterator = list.iterator();
        XmlText prevText = null;
        while(iterator.hasNext()) {
          WeakReference<OuterLanguageElement> reference = iterator.next();
          final OuterLanguageElement outerElement = reference.get();
          if (outerElement == null){
            iterator.remove();
            continue;
          }
          final FileElement file = TreeUtil.getFileElement(outerElement);
          if(file == null || file.getPsi() != psiFile) {
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

  public PsiElement findElementAt(int offset) {
    final PsiFile mainRoot = getPsi(getBaseLanguage());
    PsiElement ret = null;
    for (final Language language : getRelevantLanguages()) {
      final PsiFile psiRoot = getPsi(language);
      final PsiElement psiElement;
      psiElement = findElementAt(psiRoot, offset);
      if(psiElement == null || psiElement instanceof OuterLanguageElement) continue;
      if(ret == null || psiRoot != mainRoot) {
        ret = psiElement;
      }
    }
    return ret;
  }

  public PsiReference findReferenceAt(int offset) {
    TextRange minRange = new TextRange(0, getContents().length());
    PsiReference ret = null;
    for (final Language language : getRelevantLanguages()) {
      final PsiElement psiRoot = getPsi(language);
      final PsiReference reference = SharedPsiElementImplUtil.findReferenceAt(psiRoot, offset);
      if (reference == null) continue;
      final TextRange textRange = reference.getRangeInElement().shiftRight(reference.getElement().getNode().getStartOffset());
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
    return lang.getParserDefinition().createFile(this);
  }
}
