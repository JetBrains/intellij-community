package com.intellij.psi.impl.source.tree;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.impl.source.parsing.*;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

public class ChangeUtil implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.ChangeUtil");

  public static void addChild(final CompositeElement parent, final TreeElement child, TreeElement anchorBefore) {
    LOG.assertTrue(anchorBefore == null || anchorBefore.getTreeParent() == parent);

    int childLength = child.getTextLength();
    int offset = anchorBefore != null
                 ? anchorBefore.getStartOffset()
                 : parent.getStartOffset() + parent.getTextLength();

    PsiTreeChangeEventImpl event = null;
    PsiElement parentPsiElement = SourceTreeToPsiMap.treeElementToPsi(parent);
    PsiFile file = parentPsiElement.getContainingFile();
    boolean physical = parentPsiElement.isPhysical();
    if (physical){
      PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
      if (file != null){
        manager.invalidateFile(file);
      }
      event = new PsiTreeChangeEventImpl(manager);
      event.setParent(parentPsiElement);
      event.setFile(file);
      event.setOffset(offset);
      event.setOldLength(0);
      manager.beforeChildAddition(event);

      RepositoryManager repositoryManager = manager.getRepositoryManager();
      if (repositoryManager != null){
        repositoryManager.beforeChildAddedOrRemoved(file, parent, child);
      }
    }

    child.setTreeNext(null);
    final CharTable newCharTab = SharedImplUtil.findCharTableByTree(parent);
    final CharTable oldCharTab = SharedImplUtil.findCharTableByTree(child);
    if(newCharTab != oldCharTab)
    registerLeafsInCharTab(newCharTab, child, oldCharTab);
    if (anchorBefore != null){
      TreeUtil.insertBefore(anchorBefore, child);
    }
    else{
      TreeUtil.addChildren(parent, child);
    }

    //updateCachedLengths(parent, childLength);
    parent.subtreeChanged();

    PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
    if (physical){
      event.setChild(SourceTreeToPsiMap.treeElementToPsi(child));
      manager.childAdded(event);
    }
    else if (manager != null){
      manager.nonPhysicalChange();
    }

    checkConsistency(file);
  }

  private static void checkConsistency(PsiFile file) {
    if (LOG.isDebugEnabled() && file != null) {
      Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document != null) {
        PsiDocumentManagerImpl.checkConsistency(file, document);
      }
    }
  }

  public static void registerLeafsInCharTab(CharTable newCharTab, TreeElement child, CharTable oldCharTab) {
    if(newCharTab == oldCharTab) return;
    while(child != null){
      CharTable charTable = child.getUserData(CharTable.CHAR_TABLE_KEY);
      if (child instanceof LeafElement) {
        ((LeafElement)child).registerInCharTable(newCharTab, oldCharTab);
      }
      else {
        registerLeafsInCharTab(newCharTab, ((CompositeElement)child).firstChild, charTable != null ? charTable : oldCharTab);
      }
      if (charTable != null) {
        child.putUserData(CharTable.CHAR_TABLE_KEY, null);
      }
      child = child.getTreeNext();
    }
  }

  public static void removeChild(final CompositeElement parent, final TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == parent);

    int offset = child.getStartOffset();
    int childLength = child.getTextLength();

    PsiTreeChangeEventImpl event = null;
    PsiElement parentPsiElement = SourceTreeToPsiMap.treeElementToPsi(parent);
    PsiFile file = parentPsiElement.getContainingFile();
    boolean physical = parentPsiElement.isPhysical();
    if (physical){
      PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
      if (file != null){
        manager.invalidateFile(file);
      }
      event = new PsiTreeChangeEventImpl(manager);
      event.setParent(parentPsiElement);
      event.setChild(SourceTreeToPsiMap.treeElementToPsi(child));
      event.setFile(file);
      event.setOffset(offset);
      event.setOldLength(childLength);
      manager.beforeChildRemoval(event);

      RepositoryManager repositoryManager = manager.getRepositoryManager();
      if (repositoryManager != null){
        repositoryManager.beforeChildAddedOrRemoved(file, parent, child);
      }
    }

    child.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(child));
    TreeUtil.remove(child);

    parent.subtreeChanged();
    //updateCachedLengths(parent, -childLength);

    PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
    if (physical){
      manager.childRemoved(event);
    }
    else if (manager != null){
      manager.nonPhysicalChange();
    }
    checkConsistency(file);
  }

  public static void replaceChild(final CompositeElement parent, final TreeElement oldChild, final TreeElement newChild) {
    if (oldChild.getTreeParent() != parent){
      LOG.assertTrue(oldChild.getTreeParent() == parent);
    }

    int offset = oldChild.getStartOffset();
    int oldLength = oldChild.getTextLength();
    int newLength = newChild.getTextLength();
    final CharTable newCharTable = SharedImplUtil.findCharTableByTree(parent);
    final CharTable oldCharTable = SharedImplUtil.findCharTableByTree(newChild);

    PsiTreeChangeEventImpl event = null;
    PsiElement parentPsiElement = SourceTreeToPsiMap.treeElementToPsi(parent);
    PsiFile file = parentPsiElement.getContainingFile();
    boolean physical = parentPsiElement.isPhysical();
    if (physical){
      PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
      if (file != null){
        manager.invalidateFile(file);
      }
      event = new PsiTreeChangeEventImpl(manager);
      event.setParent(parentPsiElement);
      event.setOldChild(SourceTreeToPsiMap.treeElementToPsi(oldChild));
      event.setFile(file);
      event.setOffset(offset);
      event.setOldLength(oldLength);
      manager.beforeChildReplacement(event);

      RepositoryManager repositoryManager = manager.getRepositoryManager();
      if (repositoryManager != null){
        repositoryManager.beforeChildAddedOrRemoved(file, parent, oldChild);
        repositoryManager.beforeChildAddedOrRemoved(file, parent, newChild);
      }
    }

    newChild.setTreeNext(null);
    if(oldCharTable != newCharTable){
      registerLeafsInCharTab(newCharTable, newChild, oldCharTable);
    }
    oldChild.putUserData(CharTable.CHAR_TABLE_KEY, newCharTable);
    if(oldChild != newChild) TreeUtil.replace(oldChild, newChild);

    parent.subtreeChanged();
    //updateCachedLengths(parent, newLength - oldLength);

    PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
    if (physical){
      event.setNewChild(SourceTreeToPsiMap.treeElementToPsi(newChild));
      manager.childReplaced(event);
    }
    else if (manager != null){
      manager.nonPhysicalChange();
    }
    checkConsistency(file);
  }

  public static void replaceAllChildren(final CompositeElement parent, final CompositeElement newChildrenParent) {
    int offset = parent.getStartOffset();
    int oldLength = parent.getTextLength();
    int newLength = newChildrenParent.getTextLength();

    PsiTreeChangeEventImpl event = null;
    PsiElement parentPsiElement = SourceTreeToPsiMap.treeElementToPsi(parent);
    boolean physical = parentPsiElement.isPhysical();
    PsiFile file = parentPsiElement.getContainingFile();
    ChameleonTransforming.transformChildren(newChildrenParent);
    final TreeElement firstChild = newChildrenParent.firstChild;
    if (physical){
      PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
      manager.invalidateFile(file);
      event = new PsiTreeChangeEventImpl(manager);
      event.setParent(parentPsiElement);
      event.setFile(file);
      event.setOffset(offset);
      event.setOldLength(oldLength);
      manager.beforeChildrenChange(event);

      VirtualFile vFile = file.getVirtualFile();
      if (vFile != null){
        RepositoryManager repositoryManager = manager.getRepositoryManager();
        if (repositoryManager != null){
          ChameleonTransforming.transformChildren(parent);
          for(TreeElement child = parent.firstChild; child != null; child = child.getTreeNext()){
            repositoryManager.beforeChildAddedOrRemoved(file, parent, child);
          }

          for(TreeElement child = firstChild; child != null; child = child.getTreeNext()){
            repositoryManager.beforeChildAddedOrRemoved(file, parent, child);
          }
        }
      }
    }
    final CharTable newCharTab = SharedImplUtil.findCharTableByTree(parent);
    TreeElement oldChild = parent.firstChild;
    while(oldChild != null){
      oldChild.putUserData(CharTable.CHAR_TABLE_KEY, newCharTab);
      oldChild = oldChild.getTreeNext();
    }
    TreeUtil.removeRange(parent.firstChild, null);

    if (firstChild != null){
      final CharTable oldCharTab = SharedImplUtil.findCharTableByTree(newChildrenParent);
      registerLeafsInCharTab(newCharTab, firstChild, oldCharTab);
      TreeUtil.addChildren(parent, firstChild);
    }
    parent.setCachedLength(newLength);
    parent.subtreeChanged();
    //if (parent.getTreeParent() != null){
    //  updateCachedLengths(parent.getTreeParent(), newLength - oldLength);
    //}

    PsiManagerImpl manager = (PsiManagerImpl)parent.getManager();
    if (physical){
      manager.childrenChanged(event);
    }
    else if (manager != null){
      manager.nonPhysicalChange();
    }
    checkConsistency(file);
  }

  public static void encodeInformation(TreeElement element) {
    encodeInformation(element, element);
  }

  private static void encodeInformation(TreeElement element, TreeElement original) {
    boolean encodeRefTargets = true;
    if (original.getTreeParent() instanceof DummyHolderElement){
      DummyHolder dummyHolder = (DummyHolder)SourceTreeToPsiMap.treeElementToPsi(original.getTreeParent());
      if (dummyHolder.getContext() == null && !dummyHolder.hasImports()){ // optimization
        encodeRefTargets = false;
      }
    }
    _encodeInformation(element, original, encodeRefTargets);
  }

  private static void _encodeInformation(TreeElement element, TreeElement original, boolean encodeRefTargets) {
    if (original instanceof CompositeElement){
      if (original.getElementType() == ElementType.JAVA_CODE_REFERENCE || original.getElementType() == ElementType.REFERENCE_EXPRESSION){
        if (encodeRefTargets){
          encodeInformationInRef(element, original);
        }
      }
      else if (original.getElementType() == ElementType.MODIFIER_LIST){
        if ((original.getTreeParent().getElementType() == ElementType.FIELD || original.getTreeParent().getElementType() == ElementType.METHOD)
          && original.getTreeParent().getTreeParent().getElementType() == ElementType.CLASS &&
          ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(original.getTreeParent().getTreeParent())).isInterface()){
          element.putUserData(INTERFACE_MODIFIERS_FLAG_KEY, Boolean.TRUE);
        }
      }

      ChameleonTransforming.transformChildren((CompositeElement)element);
      ChameleonTransforming.transformChildren((CompositeElement)original);
      TreeElement child = ((CompositeElement)element).firstChild;
      TreeElement child1 = ((CompositeElement)original).firstChild;
      while(child != null){
        _encodeInformation(child, child1, encodeRefTargets);
        child = child.getTreeNext();
        child1 = child1.getTreeNext();
      }
    }
  }

  private static void encodeInformationInRef(TreeElement ref, TreeElement original) {
    if (original.getElementType() == REFERENCE_EXPRESSION){
      if (original.getTreeParent().getElementType() != REFERENCE_EXPRESSION) return; // cannot refer to class (optimization)
      PsiElement target = ((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(original)).resolve();
      if (target instanceof PsiClass){
        ref.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)target);
      }
    }
    else if (original.getElementType() == JAVA_CODE_REFERENCE){
      switch(((PsiJavaCodeReferenceElementImpl)original).getKind()){
        case PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND:
          {
            final PsiElement target = ((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(original)).resolve();
            if (target instanceof PsiClass){
              ref.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)target);
            }
          }
          break;

        case PsiJavaCodeReferenceElementImpl.PACKAGE_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND:
          break;

        default:
          LOG.assertTrue(false);
      }
    }
    else{
      LOG.assertTrue(false, "Wrong element type: " + original.getElementType());
      return;
    }
  }

  public static TreeElement decodeInformation(TreeElement element) {
    if (element instanceof CompositeElement){
      ChameleonTransforming.transformChildren((CompositeElement)element);
      TreeElement child = ((CompositeElement)element).firstChild;
      while(child != null){
        child = decodeInformation(child);
        child = child.getTreeNext();
      }

      if (element.getElementType() == ElementType.JAVA_CODE_REFERENCE || element.getElementType() == ElementType.REFERENCE_EXPRESSION){
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(element);
        final PsiClass refClass = element.getCopyableUserData(REFERENCED_CLASS_KEY);
        if (refClass != null){
          element.putCopyableUserData(REFERENCED_CLASS_KEY, null);

          if (refClass.isPhysical() || !ref.isPhysical()){ //?
            PsiManager manager = refClass.getManager();
            CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx)manager.getCodeStyleManager();
            PsiElement refElement1 = ref.resolve();
            try{
              if (refClass != refElement1 && !manager.areElementsEquivalent(refClass, refElement1)){
                if (((CompositeElement)element).findChildByRole(ChildRole.QUALIFIER) == null){ // can restore only if short (otherwise qualifier should be already restored)
                  ref = (PsiJavaCodeReferenceElement)ref.bindToElement(refClass);
                }
              }
              else{
                // shorten references to the same package and to inner classes that can be accessed by short name
                ref = (PsiJavaCodeReferenceElement)codeStyleManager.shortenClassReferences(ref, CodeStyleManagerEx.DO_NOT_ADD_IMPORTS);
              }
              element = SourceTreeToPsiMap.psiElementToTree(ref);
            }
            catch(IncorrectOperationException e){
              codeStyleManager.addImport(ref.getContainingFile(), refClass); // it may fail for local class, let's try for DummyHolder
            }
          }
        }
      }
      else if (element.getElementType() == ElementType.MODIFIER_LIST){
        if (element.getUserData(INTERFACE_MODIFIERS_FLAG_KEY) != null){
          element.putUserData(INTERFACE_MODIFIERS_FLAG_KEY, null);
          try{
            PsiModifierList modifierList = (PsiModifierList)SourceTreeToPsiMap.treeElementToPsi(element);
            if (element.getTreeParent().getElementType() == ElementType.FIELD){
              modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
              modifierList.setModifierProperty(PsiModifier.STATIC, true);
              modifierList.setModifierProperty(PsiModifier.FINAL, true);
            }
            else if (element.getTreeParent().getElementType() == ElementType.METHOD){
              modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
              modifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
            }
          }
          catch(IncorrectOperationException e){
            LOG.error(e);
          }
        }
      }
    }
    return element;
  }

  public static TreeElement copyElement(TreeElement original, CharTable table) {
    final TreeElement element = (TreeElement)original.clone();
    final PsiManager manager = original.getManager();
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(original);
    registerLeafsInCharTab(table, element, charTableByTree);
    new DummyHolder(manager, element, null, table).getTreeElement();
    encodeInformation(element, original);
//  CodeEditUtil.normalizeCloneIndent(element, original, table);
    CodeEditUtil.unindentSubtree(element, original, table);
    return element;
  }

  public static TreeElement copyToElement(PsiElement original) {
    final DummyHolder holder = new DummyHolder(original.getManager(), null);
    final FileElement holderElement = holder.getTreeElement();
    final TreeElement treeElement = _copyToElement(original, holderElement.getCharTable());
//  TreeElement treePrev = treeElement.getTreePrev(); // This is hack to support bug used in formater
    TreeUtil.addChildren(holderElement, treeElement);
//  treeElement.setTreePrev(treePrev);
    return treeElement;
  }

  private static TreeElement _copyToElement(PsiElement original, CharTable table) {
    LOG.assertTrue(original.isValid());
    if (SourceTreeToPsiMap.hasTreeElement(original)){
      return copyElement(SourceTreeToPsiMap.psiElementToTree(original), table);
    }
    else if (original instanceof PsiIdentifier){
      final String text = original.getText();
      return Factory.createLeafElement(IDENTIFIER, text.toCharArray(), 0, text.length(), -1, table);
    }
    else if (original instanceof PsiKeyword){
      final String text = original.getText();
      return Factory.createLeafElement(((PsiKeyword)original).getTokenType(), text.toCharArray(), 0, text.length(), -1, table);
    }
    else if (original instanceof PsiReferenceExpression){
      TreeElement element = createReferenceExpression(original.getManager(), original.getText(), table);
      PsiElement refElement = ((PsiJavaCodeReferenceElement)original).resolve();
      if (refElement instanceof PsiClass){
        element.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)refElement);
      }
      return element;
    }
    else if (original instanceof PsiJavaCodeReferenceElement){
      PsiElement refElement = ((PsiJavaCodeReferenceElement)original).resolve();
      if (refElement instanceof PsiClass){
        if (refElement instanceof PsiAnonymousClass){
          PsiJavaCodeReferenceElement ref = ((PsiAnonymousClass)refElement).getBaseClassReference();
          original = ref;
          refElement = ref.resolve();
        }

        boolean isFQ = false;
        if (original instanceof PsiJavaCodeReferenceElementImpl){
          int kind = ((PsiJavaCodeReferenceElementImpl)original).getKind();
          switch(kind){
            case PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND:
            case PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND:
            case PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND:
              isFQ = false;
              break;

            case PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND:
            case PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND:
              isFQ = true;
              break;

            default:
              LOG.assertTrue(false);
          }
        }

        String text = isFQ ? ((PsiClass)refElement).getQualifiedName() : original.getText();
        TreeElement element = createReference(original.getManager(), text, table);
        element.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)refElement);
        return element;
      }
      else if (refElement instanceof PsiPackage){
        return createReference(original.getManager(), original.getText(), table);
      }
      else{
        return createReference(original.getManager(), original.getText(), table);
      }
    }
    else if (original instanceof PsiCompiledElement){
      PsiElement sourceVersion = original.getNavigationElement();
      if (sourceVersion != original){
        return _copyToElement(sourceVersion, table);
      }
      TreeElement mirror = SourceTreeToPsiMap.psiElementToTree(((PsiCompiledElement)original).getMirror());
      return _copyToElement(SourceTreeToPsiMap.treeElementToPsi(mirror), table);
    }
    else if (original instanceof PsiTypeElement){
      PsiTypeElement typeElement = (PsiTypeElement)original;
      PsiType type = typeElement.getType();
      if (type instanceof PsiEllipsisType) {
        TreeElement componentTypeCopy = _copyToElement(
          new LightTypeElement(original.getManager(), ((PsiEllipsisType)type).getComponentType()),
          table
        );
        if (componentTypeCopy == null) return null;
        CompositeElement element = Factory.createCompositeElement(TYPE);
        TreeUtil.addChildren(element, componentTypeCopy);
        TreeUtil.addChildren(element, Factory.createLeafElement(ELLIPSIS, new char[]{'.', '.', '.'}, 0, 3, -1, table));
        return element;
      }
      else if (type instanceof PsiArrayType){
        TreeElement componentTypeCopy = _copyToElement(
          new LightTypeElement(original.getManager(), ((PsiArrayType)type).getComponentType()),
          table
        );
        if (componentTypeCopy == null) return null;
        CompositeElement element = Factory.createCompositeElement(TYPE);
        TreeUtil.addChildren(element, componentTypeCopy);
        TreeUtil.addChildren(element, Factory.createLeafElement(LBRACKET, new char[]{'['}, 0, 1, -1, table));
        TreeUtil.addChildren(element, Factory.createLeafElement(RBRACKET, new char[]{']'}, 0, 1, -1, table));
        return element;
      }
      else if (type instanceof PsiPrimitiveType){
        String text = typeElement.getText();
        if (text.equals("null")) return null;
        Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
        lexer.start(text.toCharArray());
        TreeElement keyword = ParseUtil.createTokenElement(lexer, table);
        CompositeElement element = Factory.createCompositeElement(TYPE);
        TreeUtil.addChildren(element, keyword);
        return element;
      } else if (type instanceof PsiWildcardType) {
        char[] buffer = original.getText().toCharArray();
        return DeclarationParsing.parseTypeText(original.getManager(), buffer, 0, buffer.length, table);
      } else {
        final PsiJavaCodeReferenceElement ref;
        if (type instanceof PsiClassReferenceType){
          PsiClassReferenceType refType = (PsiClassReferenceType)type;
          ref = refType.getReference();
        } else if (type instanceof PsiImmediateClassType) {
          final PsiImmediateClassType classType = (PsiImmediateClassType) type;
          final CompositeElement reference = createReference(original.getManager(), classType.getPresentableText(), table);
          final CompositeElement immediateTypeElement = Factory.createCompositeElement(TYPE);
          TreeUtil.addChildren(immediateTypeElement, reference);
          encodeInfoInTypeElement(immediateTypeElement, classType);
          return immediateTypeElement;
        }
        else{
          String text = original.getText();
          ref = new LightClassReference(original.getManager(), text, type.getCanonicalText(), original);
        }
        CompositeElement element = Factory.createCompositeElement(TYPE);
        TreeUtil.addChildren(element, _copyToElement(ref, table));
        return element;
      }
    }
    else{
      LOG.error("ChangeUtil.copyToElement() unknown element " + original);
      return null;
    }
  }

  private static CompositeElement createReference(PsiManager manager, String text, CharTable table){
    return Parsing.parseJavaCodeReferenceText(manager, text.toCharArray(), table);
  }

  private static TreeElement createReferenceExpression(PsiManager manager, String text, CharTable table) {
    return ExpressionParsing.parseExpressionText(manager, text.toCharArray(), 0, text.toCharArray().length, table);
  }


  private static void encodeInfoInTypeElement(CompositeElement typeElement, PsiType type) {
    if (type instanceof PsiPrimitiveType) return;
    LOG.assertTrue(typeElement.getElementType() == TYPE);
    if (type instanceof PsiArrayType) {
      final TreeElement firstChild = typeElement.firstChild;
      LOG.assertTrue(firstChild.getElementType() == TYPE);
      encodeInfoInTypeElement((CompositeElement) firstChild, ((PsiArrayType) type).getComponentType());
      return;
    }
    else if (type instanceof PsiWildcardType) {
      final PsiType bound = ((PsiWildcardType)type).getBound();
      if (bound == null) return;
      final TreeElement lastChild = typeElement.lastChild;
      if (lastChild.getElementType() != TYPE) return;
      encodeInfoInTypeElement((CompositeElement)lastChild, bound);
    }
    else if (type instanceof PsiCapturedWildcardType) {
      final PsiType bound = ((PsiCapturedWildcardType)type).getWildcard().getBound();
      if (bound == null) return;
      final TreeElement lastChild = typeElement.lastChild;
      if (lastChild.getElementType() != TYPE) return;
      encodeInfoInTypeElement((CompositeElement)lastChild, bound);
    }
    else if (type instanceof PsiIntersectionType) {
      encodeInfoInTypeElement(typeElement, ((PsiIntersectionType)type).getRepresentative());
      return;
    }
    else {
      LOG.assertTrue(type instanceof PsiClassType);
      final PsiClassType classType = (PsiClassType) type;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass referencedClass = resolveResult.getElement();
      if (referencedClass == null) return;
      final TreeElement reference = typeElement.firstChild;
      LOG.assertTrue(reference.getElementType() == JAVA_CODE_REFERENCE);

      encodeClassTypeInfoInReference((CompositeElement) reference, resolveResult.getElement(), resolveResult.getSubstitutor());
    }
  }

  private static void encodeClassTypeInfoInReference(CompositeElement reference, PsiClass referencedClass, PsiSubstitutor substitutor) {
    LOG.assertTrue(referencedClass != null);
    reference.putCopyableUserData(REFERENCED_CLASS_KEY, referencedClass);

    final PsiTypeParameterList typeParameterList = referencedClass.getTypeParameterList();
    if (typeParameterList == null) return;
    final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    if (typeParameters.length == 0) return;

    final CompositeElement referenceParameterList = (CompositeElement) reference.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    int index = 0;
    for (TreeElement child = referenceParameterList.firstChild; child != null; child = child.getTreeNext()) {
      if (child.getElementType() == TYPE) {
        final PsiType substitutedType = substitutor.substitute(typeParameters[index]);
        if (substitutedType != null) {
          encodeInfoInTypeElement((CompositeElement) child, substitutedType);
        }
        index++;
      }
    }

    final TreeElement qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
    if (qualifier != null) {
      if (referencedClass.hasModifierProperty(PsiModifier.STATIC)) return;
      final PsiClass outerClass = referencedClass.getContainingClass();
      if (outerClass != null) {
        encodeClassTypeInfoInReference((CompositeElement) qualifier, outerClass, substitutor);
      }
    }
  }

  private static final Key<PsiClass> REFERENCED_CLASS_KEY = Key.create("REFERENCED_CLASS_KEY");
  private static final Key<Boolean> INTERFACE_MODIFIERS_FLAG_KEY = Key.create("INTERFACE_MODIFIERS_FLAG_KEY");

  public static void addChildren(final CompositeElement parent,
                                 TreeElement firstChild,
                                 final TreeElement lastChild,
                                 final TreeElement anchorBefore) {
    while(firstChild != lastChild){
      final TreeElement next = firstChild.getTreeNext();
      addChild(parent, firstChild, anchorBefore);
      firstChild = next;
    }
  }

  public static void replaceAll(LeafElement[] leafElements, LeafElement merged) {
    if(leafElements.length == 0) return;
    final CompositeElement parent = leafElements[0].getTreeParent();
    if(LOG.isDebugEnabled()){
      for (int i = 0; i < leafElements.length; i++) {
        final LeafElement leafElement = leafElements[i];
        LOG.assertTrue(leafElement.getTreeParent() == parent);
      }
    }

    final PsiElement psiParent = SourceTreeToPsiMap.treeElementToPsi(parent);
    final PsiFile containingFile = psiParent.getContainingFile();
    final PsiManagerImpl manager = (PsiManagerImpl)containingFile.getManager();
    final PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(manager);
    if(containingFile.isPhysical()){
      event.setParent(psiParent);
      event.setFile(containingFile);
      event.setOffset(parent.getStartOffset());
      event.setOldLength(parent.getTextLength());
      manager.beforeChildrenChange(event);
    }
      TreeUtil.insertAfter(leafElements[0], merged);
      for (int i = 0; i < leafElements.length; i++) TreeUtil.remove(leafElements[i]);

    parent.subtreeChanged();
    if(containingFile.isPhysical()) manager.childrenChanged(event);
    checkConsistency(containingFile);
  }
}
