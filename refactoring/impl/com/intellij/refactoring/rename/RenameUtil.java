package com.intellij.refactoring.rename;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RenameUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameUtil");

  @NotNull
  public static UsageInfo[] findUsages(final PsiElement element,
                                       String newName,
                                       boolean searchInStringsAndComments,
                                       boolean searchForTextOccurences,
                                       Map<? extends PsiElement, String> allRenames) {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();

    PsiManager manager = element.getManager();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;

      PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference ref : refs) {
        result.add(new MoveRenameUsageInfo(ref.getElement(), ref, element));
      }
    }
    else {
      Collection<PsiReference> refs = ReferencesSearch.search(element).findAll();
      for (PsiReference ref : refs) {
        PsiElement referenceElement = ref.getElement();
        result.add(new MoveRenameUsageInfo(referenceElement, ref, ref.getRangeInElement().getStartOffset(),
                                           ref.getRangeInElement().getEndOffset(), element,
                                           false));
      }
    }

    for(RenameCollisionDetector collisionDetector: Extensions.getExtensions(RenameCollisionDetector.EP_NAME)) {
      collisionDetector.findCollisions(element, newName, allRenames, result);
    }


    if (searchInStringsAndComments && !(element instanceof PsiDirectory)) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, false
                                                                                    ? NonCodeSearchDescriptionLocation.NON_JAVA
                                                                                    : NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, false);
        TextOccurrencesUtil.UsageInfoFactory factory = new NonCodeUsageInfoFactory(element, stringToReplace);
        TextOccurrencesUtil.addUsagesInStringsAndComments(element, stringToSearch, result, factory);
      }
    }


    if (searchForTextOccurences && !(element instanceof PsiDirectory)) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, true
                                                                                    ? NonCodeSearchDescriptionLocation.NON_JAVA
                                                                                    : NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);

      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, true);
        addTextOccurence(element, result, projectScope, stringToSearch, stringToReplace);

        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          if (aClass.getParent() instanceof PsiClass) {
            final String dollaredStringToSearch = ClassUtil.getJVMClassName(aClass);
            final String dollaredStringToReplace = dollaredStringToSearch == null ? null : RefactoringUtil.getNewInnerClassName(aClass, dollaredStringToSearch, newName);
            if (dollaredStringToReplace != null) {
              addTextOccurence(aClass, result, projectScope, dollaredStringToSearch, dollaredStringToReplace);
            }
          }
        }
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private static void addTextOccurence(final PsiElement element, final List<UsageInfo> result, final GlobalSearchScope projectScope,
                                       final String stringToSearch,
                                       final String stringToReplace) {
    TextOccurrencesUtil.UsageInfoFactory factory = new TextOccurrencesUtil.UsageInfoFactory() {
      public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
        TextRange textRange = usage.getTextRange();
        int start = textRange == null ? 0 : textRange.getStartOffset();
        return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element, stringToReplace);
      }
    };
    TextOccurrencesUtil.addTextOccurences(element, stringToSearch, projectScope, result, factory);
  }


  public static void buildPackagePrefixChangedMessage(final VirtualFile[] virtualFiles, StringBuffer message, final String qualifiedName) {
    if (virtualFiles.length > 0) {
      message.append(RefactoringBundle.message("package.occurs.in.package.prefixes.of.the.following.source.folders.n", qualifiedName));
      for (final VirtualFile virtualFile : virtualFiles) {
        message.append(virtualFile.getPresentableUrl()).append("\n");
      }
      message.append(RefactoringBundle.message("these.package.prefixes.will.be.changed"));
    }
  }

  private static String getStringToReplace(PsiElement element, String newName, boolean nonJava) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner psiMetaOwner = (PsiMetaOwner)element;
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }
    if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
      element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    }

    if (element instanceof PsiPackage) {
      if (nonJava) {
        String qName = ((PsiPackage)element).getQualifiedName();
        int index = qName.lastIndexOf('.');
        return index < 0 ? newName : qName.substring(0, index + 1) + newName;
      }
      else {
        return newName;
      }
    }
    else if (element instanceof PsiClass) {
      if (nonJava) {
        final PsiClass aClass = (PsiClass)element;
        return getQualifiedNameAfterRename(aClass, newName);
      }
      else {
        return newName;
      }
    }
    else if (element instanceof PsiNamedElement) {
      return newName;
    }
    else {
      LOG.error("Unknown element type");
      return null;
    }
  }

  public static String getQualifiedNameAfterRename(final PsiClass aClass, String newName) {
    String qName = aClass.getQualifiedName();
    return getQualifiedNameAfterRename(qName, newName);
  }

  static String getQualifiedNameAfterRename(String qName, String newName) {
    if (qName == null) return newName;
    int index = qName.lastIndexOf('.');
    return index < 0 ? newName : qName.substring(0, index + 1) + newName;
  }

  public static void checkRename(PsiElement element, String newName) throws IncorrectOperationException {
    if (element instanceof PsiFileSystemItem) {
      ((PsiFileSystemItem)element).checkSetName(newName);
    }
    if (element instanceof PsiPackage) {
      ((PsiPackage)element).checkSetName(newName);
    }
  }

  public static void doRename(final PsiElement element, String newName, UsageInfo[] usages, final Project project,
                              RefactoringElementListener listener) {
    try {
      if (element instanceof PsiDirectory) {
        doRenameDirectory((PsiDirectory)element, newName, usages, listener);
      }
      else if (element instanceof PsiClass) {
        doRenameClass((PsiClass)element, newName, usages, listener);
      }
      else if (element instanceof PsiMethod) {
        doRenameMethod((PsiMethod)element, newName, usages, listener);
      }
      else if (element instanceof PsiVariable) {
        doRenameVariable((PsiVariable)element, newName, usages, listener);
      }
      else if (element instanceof XmlAttribute) {
        doRenameXmlAttribute((XmlAttribute)element, newName, listener);
      }
      else if (element instanceof XmlAttributeValue) {
        doRenameXmlAttributeValue((XmlAttributeValue)element, newName, usages, listener);
      }
      else if (element instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)element;
        psiPackage.handleQualifiedNameChange(getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName));
        doRenameGenericNamedElement(element, newName, usages, listener);
      }
      else {
        doRenameGenericNamedElement(element, newName, usages, listener);
      }
    }
    catch (final IncorrectOperationException e) {
      // may happen if the file or package cannot be renamed. e.g. locked by another application
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(e);
        //LOG.error(e);
        //return;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("rename.title"), e.getMessage(), HelpID.getRenameHelpID(element), project);
          }
        });
    }
  }

  private static void doRenameGenericNamedElement(PsiElement namedElement, String newName, UsageInfo[] usages, RefactoringElementListener listener)
    throws IncorrectOperationException {
    PsiWritableMetaData writableMetaData = null;
    if (namedElement instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)namedElement).getMetaData();
      if (metaData instanceof PsiWritableMetaData) {
        writableMetaData = (PsiWritableMetaData)metaData;
      }
    }
    if (writableMetaData == null && !(namedElement instanceof PsiNamedElement)) {
      LOG.error("Unknown element type");
    }

    for (UsageInfo usage : usages) {
      rename(usage, newName);
    }

    if (writableMetaData != null) {
      writableMetaData.setName(newName);
    }
    else {
      ((PsiNamedElement)namedElement).setName(newName);
    }

    listener.elementRenamed(namedElement);
  }

  private static void doRenameXmlAttribute(XmlAttribute attribute,
                                           String newName,
                                           RefactoringElementListener listener) {
    try {
      final PsiElement element = attribute.setName(newName);
      listener.elementRenamed(element);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void doRenameXmlAttributeValue(XmlAttributeValue value,
                                                String newName,
                                                UsageInfo[] infos,
                                                RefactoringElementListener listener)
    throws IncorrectOperationException {
    LOG.assertTrue(value != null);
    LOG.assertTrue(value.isValid());

    renameAll(value, infos, newName, value.getValue());

    PsiManager psiManager = value.getManager();
    LOG.assertTrue(psiManager != null);
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(psiManager.getProject()).createFileFromText("dummy.xml", "<a attr=\"" + newName + "\"/>");
    final PsiElement element = value.replace(file.getDocument().getRootTag().getAttributes()[0].getValueElement());
    listener.elementRenamed(element);
  }

  private static void renameAll(PsiElement originalElement, UsageInfo[] infos, String newName, String originalName)
    throws IncorrectOperationException {
    if (newName.equals(originalName)) return;
    final PsiSearchHelper searchHelper = originalElement.getManager().getSearchHelper();
    Queue<PsiReference> queue = new Queue<PsiReference>(infos.length);
    for (UsageInfo info : infos) {
      if (info.getElement() == null) continue;
      PsiReference ref = info.getReference();
      if (ref == null) continue;
      queue.addLast(ref);
    }

    while(!queue.isEmpty()) {
      final PsiReference reference = queue.pullFirst();
      final PsiElement oldElement = reference.getElement();
      if (!oldElement.isValid() || oldElement == originalElement) continue;
      final PsiElement newElement = reference.handleElementRename(newName);
      if (!oldElement.isValid()) {
        final PsiReference[] references =
          ReferencesSearch.search(originalElement, new LocalSearchScope(newElement), false).toArray(new PsiReference[0]);
        for (PsiReference psiReference : references) {
          queue.addLast(psiReference);
        }
      }
    }
  }

  /**
   * renames a directory and corrects references to the package that corresponds to this directory
   */
  private static void doRenameDirectory(PsiDirectory aDirectory,
                                        String newName,
                                        UsageInfo[] usages,
                                        RefactoringElementListener listener) throws IncorrectOperationException {
    // rename all non-package statement references
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) != null) continue;
      rename(usage, newName);
    }

    //rename package statement
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) == null) continue;
      rename(usage, newName);
    }

    aDirectory.setName(newName);
    listener.elementRenamed(aDirectory);
  }

  private static void rename(UsageInfo info, String newName) throws IncorrectOperationException {
    if (info.getElement() == null) return;
    PsiReference ref = info.getReference();
    if (ref == null) return;
    ref.handleElementRename(newName);
  }

  private static void doRenameClass(PsiClass aClass,
                                    String newName,
                                    UsageInfo[] usages,
                                    RefactoringElementListener listener) throws IncorrectOperationException {
    ArrayList<UsageInfo> postponedCollisions = new ArrayList<UsageInfo>();
    // rename all references
    for (final UsageInfo usage : usages) {
      if (usage instanceof ResolvableCollisionUsageInfo) {
        if (usage instanceof CollidingClassImportUsageInfo) {
          ((CollidingClassImportUsageInfo)usage).getImportStatement().delete();
        }
        else {
          postponedCollisions.add(usage);
        }
      }
    }

    // do actual rename
    ChangeContextUtil.encodeContextInfo(aClass, true);
    PsiFile psiFile = aClass.getContainingFile();
    Document document = psiFile == null ? null : PsiDocumentManager.getInstance(aClass.getProject()).getDocument(psiFile);
    aClass.setName(newName);

    for (UsageInfo usage : usages) {
      if (!(usage instanceof ResolvableCollisionUsageInfo)) {
        final PsiReference ref = usage.getReference();
        if (ref == null) continue;
        try {
          ref.bindToElement(aClass);
        }
        catch (IncorrectOperationException e) {//fall back to old scheme
          ref.handleElementRename(newName);
        }
      }
    }
    
    ChangeContextUtil.decodeContextInfo(aClass, null, null); //to make refs to other classes from this one resolve to their old referent

    // resolve collisions
    for (UsageInfo postponedCollision : postponedCollisions) {
      ClassHidesImportedClassUsageInfo collision = (ClassHidesImportedClassUsageInfo) postponedCollision;
      collision.resolveCollision();
    }
    listener.elementRenamed(aClass);
    if (document != null) {
      // make highlighting consistent
      ((DocumentEx)document).setModificationStamp(psiFile.getModificationStamp());
    }
  }

  private static void doRenameMethod(PsiMethod method,
                                     String newName,
                                     UsageInfo[] usages,
                                     RefactoringElementListener listener) throws IncorrectOperationException {
    // do actual rename of overriding/implementing methods and of references to all them
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      if (!(element instanceof PsiMethod)) {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = usage.getReference();
        } else {
          ref = element.getReference();
        }
        if (ref != null) {
          ref.handleElementRename(newName);
        }
      }
    }

    // do actual rename of method
    method.setName(newName);
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiMethod) {
        ((PsiMethod)element).setName(newName);
      }
    }
    listener.elementRenamed(method);
  }

  private static void doRenameVariable(PsiVariable variable,
                                       String newName,
                                       UsageInfo[] usages,
                                       RefactoringElementListener listener) throws IncorrectOperationException {
    List<FieldHidesOuterFieldUsageInfo> outerHides = new ArrayList<FieldHidesOuterFieldUsageInfo>();
    // rename all references
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      if (usage instanceof LocalHidesFieldUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiElement resolved = collidingRef.resolve();

        if (resolved instanceof PsiField) {
          qualifyField((PsiField)resolved, collidingRef, newName);
        }
        else {
          // do nothing
        }
      }
      else if (usage instanceof FieldHidesOuterFieldUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiField resolved = (PsiField)collidingRef.resolve();
        outerHides.add(new FieldHidesOuterFieldUsageInfo(element, resolved));
      }
      else {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = usage.getReference();
        }
        else {
          ref = element.getReference();
        }
        if (ref != null) {
          PsiElement newElem = ref.handleElementRename(newName);
          if (variable instanceof PsiField) {
            fixPossibleNameCollisionsForFieldRenaming((PsiField)variable, newName, newElem);
          }
        }
      }
      }
    // do actual rename
    variable.setName(newName);
    listener.elementRenamed(variable);

    for (FieldHidesOuterFieldUsageInfo usage : outerHides) {
      final PsiElement element = usage.getElement();
      PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
      PsiField field = (PsiField)usage.getReferencedElement();
      PsiReferenceExpression ref = createFieldReference(field, collidingRef);
      collidingRef.replace(ref);
    }
  }


  private static void fixPossibleNameCollisionsForFieldRenaming(PsiField field, String newName, PsiElement replacedOccurence) throws IncorrectOperationException {
    if (!(replacedOccurence instanceof PsiReferenceExpression)) return;
    PsiElement elem = ((PsiReferenceExpression)replacedOccurence).resolve();

    if (elem == null || elem == field) {
      // If reference is unresolved, then field is not hidden by anyone...
      return;
    }

    if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter) {
      qualifyField(field, replacedOccurence, newName);
    }
  }

  private static void qualifyField(PsiField field, PsiElement occurence, String newName) throws IncorrectOperationException {
    PsiManager psiManager = occurence.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(factory.createReferenceExpression(field.getContainingClass()));
      occurence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("this." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createFieldReference(PsiField field, PsiElement context) throws IncorrectOperationException {
    final PsiManager manager = field.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final String name = field.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    if (manager.areElementsEquivalent(resolved, field)) return ref;
    final PsiJavaCodeReferenceElement qualifier;
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiReferenceExpression)ref.getQualifierExpression();
      final PsiClass containingClass = field.getContainingClass();
      final PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
      qualifier.replace(classReference);
    }
    else {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
      resolved = ref.resolve();
      if (manager.areElementsEquivalent(resolved, field)) return ref;
      ref = (PsiReferenceExpression) factory.createExpressionFromText("A.this." + name, null);
      qualifier = ((PsiThisExpression)ref.getQualifierExpression()).getQualifier();
      final PsiClass containingClass = field.getContainingClass();
      final PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(containingClass);
      qualifier.replace(classReference);
    }
    return ref;
  }

  public static void removeConflictUsages(Set<UsageInfo> usages) {
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      if (usageInfo instanceof UnresolvableCollisionUsageInfo) {
        iterator.remove();
      }
    }
  }

  public static Collection<String> getConflictDescriptions(UsageInfo[] usages) {
    ArrayList<String> descriptions = new ArrayList<String>();

    for (UsageInfo usage : usages) {
      if (usage instanceof UnresolvableCollisionUsageInfo) {
        descriptions.add(((UnresolvableCollisionUsageInfo)usage).getDescription());
      }
    }
    return descriptions;
  }

  public static void buildMultipleDirectoriesInPackageMessage(StringBuffer message,
                                                              PsiPackage aPackage,
                                                              PsiDirectory[] directories) {
    message.append(RefactoringBundle.message("multiple.directories.correspond.to.package"));
    message.append(aPackage.getQualifiedName());
    message.append(" :\n\n");
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (i > 0) {
        message.append("\n");
      }
      message.append(directory.getVirtualFile().getPresentableUrl());
    }
  }
}
