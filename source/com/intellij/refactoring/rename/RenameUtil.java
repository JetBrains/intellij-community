package com.intellij.refactoring.rename;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.Queue;

import java.util.*;
import java.util.regex.Pattern;

public class RenameUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameUtil");
  public static boolean ourRenameXmlAttributeValuesInReverseOrder = false;

  public static UsageInfo[] findUsages(final PsiElement element,
                                       String newName,
                                       boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles) {
    final List<UsageInfo> results = new ArrayList<UsageInfo>();

    PsiManager manager = element.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;

      PsiReference[] refs = helper.findReferencesIncludingOverriding(method, projectScope, true);
      for (int i = 0; i < refs.length; i++) {
        PsiReference ref = refs[i];
        results.add(new MoveRenameUsageInfo(ref.getElement(), ref, element));
      }

      PsiElement[] overridings = helper.findOverridingMethods(method, projectScope, true);
      for (int i = 0; i < overridings.length; i++) {
        PsiElement element1 = overridings[i];
        results.add(new MoveRenameUsageInfo(element1, null, element));
      }
    }
    else {
      PsiReference[] refs = helper.findReferences(element, projectScope, false);
      final ClassCollisionsDetector classCollisionsDetector;
      if (element instanceof PsiClass) {
        classCollisionsDetector = new ClassCollisionsDetector((PsiClass)element);
      }
      else {
        classCollisionsDetector = null;
      }
      for (int i = 0; i < refs.length; i++) {
        PsiReference ref = refs[i];
        PsiElement referenceElement = ref.getElement();
        results.add(
          new MoveRenameUsageInfo(referenceElement, ref, ref.getRangeInElement().getStartOffset(),
                                  ref.getRangeInElement().getEndOffset(), element,
                                  referenceElement instanceof XmlElement));
        if (classCollisionsDetector == null) {
          addLocalsCollisions(element, referenceElement, newName, results);
        }
        else {
          classCollisionsDetector.addClassCollisions(referenceElement, newName, results);
        }
      }
    }

    RenameUtil.findUnresolvableLocalsCollisions(element, newName, results);
    RenameUtil.findUnresolvableMemberCollisions(element, newName, results);


    if (searchInStringsAndComments && !(element instanceof PsiDirectory)) {
      String stringToSearch = RefactoringUtil.getStringToSearch(element, false);
      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, false);
        RefactoringUtil.UsageInfoFactory factory = new RefactoringUtil.UsageInfoFactory() {
          public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
            int start = usage.getTextRange().getStartOffset();
            return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element, stringToReplace);
          }
        };
        RefactoringUtil.addUsagesInStringsAndComments(element, stringToSearch, results, factory);
      }
    }


    if (searchInNonJavaFiles && !(element instanceof PsiDirectory)) {
      String stringToSearch = RefactoringUtil.getStringToSearch(element, true);

      if (stringToSearch != null) {
        final String stringToReplace = getStringToReplace(element, newName, true);
        RefactoringUtil.UsageInfoFactory factory = new RefactoringUtil.UsageInfoFactory() {
          public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
            int start = usage.getTextRange().getStartOffset();
            return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element, stringToReplace);
          }
        };
        RefactoringUtil.addUsagesInNonJavaFiles(element, stringToSearch, projectScope, results, factory);

        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          if (aClass.getParent() instanceof PsiClass) {
            final String dollaredStringToSearch = RefactoringUtil.getInnerClassNameForClassLoader(aClass);
            final String dollaredStringToReplace =
              RefactoringUtil.getNewInnerClassName(aClass, dollaredStringToSearch, newName);
            RefactoringUtil.UsageInfoFactory dollaredFactory = new RefactoringUtil.UsageInfoFactory() {
              public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
                int start = usage.getTextRange().getStartOffset();
                return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element, dollaredStringToReplace);
              }
            };
            RefactoringUtil.addUsagesInNonJavaFiles(aClass, dollaredStringToSearch, projectScope, results,
                                                    dollaredFactory);
          }
        }
      }
    }

    return results.toArray(new UsageInfo[results.size()]);
  }

  public static void buildPackagePrefixChangedMessage(final VirtualFile[] virtualFiles, StringBuffer message, final String qualifiedName) {
    if (virtualFiles.length > 0) {
      message.append("Package " + qualifiedName + " occurs in package prefixes of the following source folders:\n");
      for (int i = 0; i < virtualFiles.length; i++) {
        final VirtualFile virtualFile = virtualFiles[i];
        message.append(virtualFile.getPresentableUrl() + "\n");
      }
      message.append("These package prefixes will be changed.");
    }
  }

  private static class ClassCollisionsDetector {
    final HashSet<PsiFile> myProcessedFiles = new HashSet<PsiFile>();
    final PsiClass myRenamedClass;
    private String myRenamedClassQualifiedName;

    public ClassCollisionsDetector(PsiClass renamedClass) {
      myRenamedClass = renamedClass;
      myRenamedClassQualifiedName = myRenamedClass.getQualifiedName();
    }

    public void addClassCollisions(PsiElement referenceElement, String newName, List<UsageInfo> results) {
      final PsiClass renamedClass = myRenamedClass;
      final PsiResolveHelper resolveHelper = referenceElement.getManager().getResolveHelper();
      final PsiSearchHelper searchHelper = referenceElement.getManager().getSearchHelper();
      final PsiClass aClass = resolveHelper.resolveReferencedClass(newName, referenceElement);
      if (aClass == null) return;
      final PsiFile containingFile = referenceElement.getContainingFile();
      final String text = referenceElement.getText();
      if (Comparing.equal(myRenamedClassQualifiedName, removeSpaces(text))) return;
      if (myProcessedFiles.contains(containingFile)) return;
      final PsiReference[] references = searchHelper.findReferences(aClass, new LocalSearchScope(containingFile),
                                                                    false);
      for (int i = 0; i < references.length; i++) {
        PsiReference reference = references[i];
        final PsiElement collisionReferenceElement = reference.getElement();
        if (collisionReferenceElement instanceof PsiJavaCodeReferenceElement) {
          final PsiElement parent = collisionReferenceElement.getParent();
          if (!(parent instanceof PsiImportStatement)) {
            if (aClass.getQualifiedName() != null) {
              results.add(new ClassHidesImportedClassUsageInfo((PsiJavaCodeReferenceElement)collisionReferenceElement,
                                                               renamedClass, aClass));
            } else {
              results.add(new ClassHidesUnqualifiableClassUsageInfo((PsiJavaCodeReferenceElement) collisionReferenceElement,
                                                                    renamedClass, aClass));
            }
          }
          else {
            results.add(new CollidingClassImportUsageInfo((PsiImportStatement)parent, renamedClass));
          }
        }
      }
      myProcessedFiles.add(containingFile);
    }
  }

  private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s");

  private static String removeSpaces(String s) {
    return WHITE_SPACE_PATTERN.matcher(s).replaceAll("");
  }

  public static void findUnresolvableMemberCollisions(final PsiElement element, final String newName, List<UsageInfo> result) {
    try {
      PsiManager manager = element.getManager();
      final PsiElementFactory factory = manager.getElementFactory();
      final PsiSearchHelper helper = manager.getSearchHelper();
      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());

      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return;
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) return;
        PsiClass[] inheritors = helper.findInheritors(containingClass, projectScope, true);

        PsiMethod prototype = (PsiMethod)element.copy();
        prototype.getNameIdentifier().replace(factory.createIdentifier(newName));

        final PsiMethod existingMethod = containingClass.findMethodBySignature(prototype, true);
        if (existingMethod != null && manager.getResolveHelper().isAccessible(existingMethod, containingClass, null)) {
          result.add(new MemberExistsUsageInfo(existingMethod, method));
        }

        for (int i = 0; i < inheritors.length; i++) {
          PsiClass inheritor = inheritors[i];

          PsiMethod conflictingMethod = inheritor.findMethodBySignature(prototype, false);
          if (conflictingMethod != null) {
            result.add(new SubmemberHidesMemberUsageInfo(conflictingMethod, method));
          }
        }

      }
      else if (element instanceof PsiField) {
        final PsiField field = (PsiField)element;
        if (field.getContainingClass() == null) return;
        if (field.hasModifierProperty(PsiModifier.PRIVATE)) return;
        final PsiClass containingClass = field.getContainingClass();
        final PsiField existingField = containingClass.findFieldByName(newName, true);
        if (existingField != null && manager.getResolveHelper().isAccessible(existingField, containingClass, null)) {
          result.add(new MemberExistsUsageInfo(existingField, field));
        }
        PsiClass[] inheritors = helper.findInheritors(containingClass, projectScope, true);
        for (int i = 0; i < inheritors.length; i++) {
          PsiClass inheritor = inheritors[i];

          PsiField conflictingField = inheritor.findFieldByName(newName, false);
          if (conflictingField != null) {
            result.add(new SubmemberHidesMemberUsageInfo(conflictingField, field));
          }
        }
      }
      else if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;
        if (aClass.getParent() instanceof PsiClass) {
          PsiClass[] inheritors = helper.findInheritors((PsiClass)aClass.getParent(), projectScope, true);
          for (int i = 0; i < inheritors.length; i++) {
            PsiClass inheritor = inheritors[i];

            PsiClass[] inners = inheritor.getInnerClasses();
            for (int j = 0; j < inners.length; j++) {
              PsiClass inner = inners[j];

              if (inner.getName().equals(newName)) {
                result.add(new SubmemberHidesMemberUsageInfo(inner, aClass));
              }
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static class ConflictingLocalVariablesVisitor extends PsiRecursiveElementVisitor {
    protected final String myName;
    protected RenameUtil.CollidingVariableVisitor myCollidingNameVisitor;

    public ConflictingLocalVariablesVisitor(String newName, RenameUtil.CollidingVariableVisitor collidingNameVisitor) {
      myName = newName;
      myCollidingNameVisitor = collidingNameVisitor;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    public void visitClass(PsiClass aClass) {
    }

    public void visitVariable(PsiVariable variable) {
      if (myName.equals(variable.getName())) {
        myCollidingNameVisitor.visitCollidingElement(variable);
      }
    }
  }

  public static void findUnresolvableLocalsCollisions(final PsiElement element, final String newName,
                                                      final List<UsageInfo> result) {
    if (!(element instanceof PsiLocalVariable || element instanceof PsiParameter)) {
      return;
    }


    PsiElement scope;
    PsiElement anchor = null;
    if (element instanceof PsiLocalVariable) {
      scope = RefactoringUtil.getVariableScope((PsiLocalVariable)element);
      if (!(element instanceof ImplicitVariable)) {
        anchor = element.getParent();
      }
    }
    else {
      // element is a PsiParameter
      scope = ((PsiParameter)element).getDeclarationScope();
    }
    LOG.assertTrue(scope != null);

    final CollidingVariableVisitor collidingNameVisitor = new CollidingVariableVisitor() {
      public void visitCollidingElement(PsiVariable collidingVariable) {
        if (collidingVariable.equals(element) || collidingVariable instanceof PsiField) return;
        LocalHidesRenamedLocalUsageInfo collision = new LocalHidesRenamedLocalUsageInfo(element, collidingVariable);
        result.add(collision);
      }
    };

    visitLocalsCollisions(element, newName, scope, anchor, collidingNameVisitor);


    /*PsiElement place = scope.getLastChild();
    PsiResolveHelper helper = place.getManager().getResolveHelper();
    PsiVariable refVar = helper.resolveReferencedVariable(newName, place, null);

    if (refVar != null) {
      LocalHidesRenamedLocalUsageInfo collision = new LocalHidesRenamedLocalUsageInfo(element, refVar);
      result.add(collision);
    }*/
  }

  public static void visitLocalsCollisions(PsiElement element, final String newName,
                                           PsiElement scope,
                                           PsiElement place,
                                           final CollidingVariableVisitor collidingNameVisitor) {
    if (scope == null) return;
    visitDownstreamCollisions(scope, place, newName, collidingNameVisitor);
    visitUpstreamLocalCollisions(element, scope, newName, collidingNameVisitor);
  }

  private static void visitDownstreamCollisions(PsiElement scope, PsiElement place, final String newName,
                                               final CollidingVariableVisitor collidingNameVisitor
                                               ) {
    ConflictingLocalVariablesVisitor collector =
      new ConflictingLocalVariablesVisitor(newName, collidingNameVisitor);
    if (place == null) {
      scope.accept(collector);
    }
    else {
      LOG.assertTrue(place.getParent() == scope);
      for (PsiElement sibling = place; sibling != null; sibling = sibling.getNextSibling()) {
        sibling.accept(collector);
      }

    }
  }

  public interface CollidingVariableVisitor {
    void visitCollidingElement(PsiVariable collidingVariable);
  }

  public static void visitUpstreamLocalCollisions(PsiElement element, PsiElement scope,
                                                  String newName,
                                                  final CollidingVariableVisitor collidingNameVisitor) {
    final PsiVariable collidingVariable = scope.getManager().getResolveHelper().resolveReferencedVariable(newName,
                                                                                                          scope);
    if (collidingVariable instanceof PsiLocalVariable || collidingVariable instanceof PsiParameter) {
      final PsiElement commonParent = PsiTreeUtil.findCommonParent(element, collidingVariable);
      if (commonParent != null) {
        PsiElement current = element;
        while (current != null && current != commonParent) {
          if (current instanceof PsiMethod || current instanceof PsiClass) {
            return;
          }
          current = current.getParent();
        }
      }
    }

    if (collidingVariable != null) {
      collidingNameVisitor.visitCollidingElement(collidingVariable);
    }
  }


  private static void addLocalsCollisions(PsiElement element, PsiElement ref,
                                          String newName, List<UsageInfo> results) {
    if (!(element instanceof PsiLocalVariable) && !(element instanceof PsiParameter)) return;

    PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
    if (containingClass == null) return;


    PsiElement scopeElement;
    if (element instanceof PsiLocalVariable) {
      scopeElement = RefactoringUtil.getVariableScope((PsiLocalVariable)element);
    }
    else { // Parameter
      scopeElement = ((PsiParameter) element).getDeclarationScope();
    }

    LOG.assertTrue(scopeElement != null);

    PsiField field = containingClass.findFieldByName(newName, true);
    if (field == null) return;

    PsiSearchHelper helper = ref.getManager().getSearchHelper();
    PsiReference[] collidingRefs = helper.findReferences(field, new LocalSearchScope(scopeElement), false);
    for (int i = 0; i < collidingRefs.length; i++) {
      PsiElement collidingRef = collidingRefs[i].getElement();

      if (collidingRef instanceof PsiReferenceExpression
          && ((PsiReferenceExpression)collidingRef).getQualifierExpression() == null) {
        results.add(new LocalHidesFieldUsageInfo(collidingRef, element));
      }
    }
  }

  private static String getStringToReplace(PsiElement element, String newName, boolean nonJava) {
    if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
      element = ((PsiDirectory)element).getPackage();
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
    int index = qName != null ? qName.lastIndexOf('.') : -1;
    return index < 0 ? newName : qName.substring(0, index + 1) + newName;
  }

  public static void checkRename(PsiElement element, String newName) throws IncorrectOperationException {
    if (element instanceof PsiDirectory) {
      ((PsiDirectory)element).checkSetName(newName);
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
      else if (element instanceof XmlTag) {
        doRenameXmlTag((XmlTag)element, newName, listener);
      }
      else if (element instanceof XmlAttribute) {
        doRenameXmlAttribute((XmlAttribute)element, newName, listener);
      }
      else if (element instanceof XmlAttributeValue) {
        doRenameXmlAttributeValue((XmlAttributeValue)element, newName, usages, listener);
      }
      else if (element instanceof PsiPointcutDef) {
        doRenamePointcutDef((PsiPointcutDef)element, newName, usages, listener);
      }
      else if (element instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)element;
        psiPackage.handleQualifiedNameChange(getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName));
      }
      else if (element instanceof PsiNamedElement) {
        doRenameGenericNamedElement((PsiNamedElement) element, newName, usages, listener);
      }
      else {
        LOG.error("Unknown element type");
      }
    }
    catch (final IncorrectOperationException e) {
      // may happen if the file or package cannot be renamed. e.g. locked by another application
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(e);
        return;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            RefactoringMessageUtil.showErrorMessage("Rename", e.getMessage(), HelpID.getRenameHelpID(element), project);
          }
        });
    }
  }

  private static void doRenameGenericNamedElement(PsiNamedElement namedElement, String newName, UsageInfo[] usages, RefactoringElementListener listener)
    throws IncorrectOperationException {
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      rename(usage, newName);
    }
    namedElement.setName(newName);
    listener.elementRenamed(namedElement);
  }

  private static void doRenamePointcutDef(PsiPointcutDef pointcutDef,
                                          String newName,
                                          UsageInfo[] usages,
                                          RefactoringElementListener listener) throws IncorrectOperationException {
    PsiManager manager = pointcutDef.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    PsiIdentifier newNameIdentifier = factory.createIdentifier(newName);

    pointcutDef.getNameIdentifier().replace(newNameIdentifier);

    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      rename(usage, newName);
    }

    listener.elementRenamed(pointcutDef);
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
    PsiElementFactory elementFactory = psiManager.getElementFactory();
    LOG.assertTrue(elementFactory != null);

    XmlFile file = (XmlFile)elementFactory.createFileFromText("dummy.xml", "<a attr=\"" + newName + "\"/>");
    final PsiElement element = value.replace(file.getDocument().getRootTag().getAttributes()[0].getValueElement());
    listener.elementRenamed(element);
  }

  private static void renameAll(PsiElement originalElement, UsageInfo[] infos, String newName, String originalName)
    throws IncorrectOperationException {
    if (newName.equals(originalName)) return;
    final PsiSearchHelper searchHelper = originalElement.getManager().getSearchHelper();
    Queue<PsiReference> queue = new Queue<PsiReference>(infos.length);
    if (ourRenameXmlAttributeValuesInReverseOrder) {
      LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
      ArrayList<UsageInfo> tmp = new ArrayList<UsageInfo>(Arrays.asList(infos));
      Collections.reverse(tmp);
      infos = tmp.toArray(new UsageInfo[tmp.size()]);
    }
    for (int i = 0; i < infos.length; i++) {
      UsageInfo info = infos[i];
      if (info.getElement() == null || !info.getElement().isValid()) continue;
      PsiReference ref = ((MoveRenameUsageInfo)info).reference;
      if (ref == null) continue;
      queue.addLast(ref);
    }

    while(!queue.isEmpty()) {
      final PsiReference reference = queue.pullFirst();
      final PsiElement oldElement = reference.getElement();
      if (!oldElement.isValid()) continue;
      final PsiElement newElement = reference.handleElementRename(newName);
      if (!oldElement.isValid()) {
        final PsiReference[] references = searchHelper.findReferences(originalElement, new LocalSearchScope(newElement), false);
        for (int i = 0; i < references.length; i++) {
          PsiReference psiReference = references[i];
          queue.addLast(psiReference);
        }
      }
    }
  }

  private static void doRenameXmlTag(XmlTag xmlTag, String newName, RefactoringElementListener listener) {
    try {
      XmlTag newTag = xmlTag.getManager().getElementFactory().createTagFromText(
        "<" + xmlTag.getName() + ">" + newName + "</" + xmlTag.getName() + ">");
      final PsiElement element = xmlTag.replace(newTag);
      listener.elementRenamed(element);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
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
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) != null) continue;
      rename(usage, newName);
    }

    //rename package statement
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) == null) continue;
      rename(usage, newName);
    }

    aDirectory.setName(newName);
    listener.elementRenamed(aDirectory);
  }

  private static void rename(UsageInfo info, String newName) throws IncorrectOperationException {
    if (info.getElement() == null || !info.getElement().isValid()) return;
    PsiReference ref = ((MoveRenameUsageInfo)info).reference;
    if (ref == null) return;
    ref.handleElementRename(newName);
  }

  private static void doRenameClass(PsiClass aClass,
                                    String newName,
                                    UsageInfo[] usages,
                                    RefactoringElementListener listener) throws IncorrectOperationException {
    ArrayList<UsageInfo> postponedCollisions = new ArrayList<UsageInfo>();
    // rename all references
    for (int i = 0; i < usages.length; i++) {
      final UsageInfo usage = usages[i];
      if (!(usage instanceof ResolvableCollisionUsageInfo)) {
        rename(usage, newName);
      }
      else {
        if (usage instanceof CollidingClassImportUsageInfo) {
          ((CollidingClassImportUsageInfo)usage).getImportStatement().delete();
        }
        else {
          postponedCollisions.add(usage);
        }
      }
    }

    // do actual rename
    aClass.setName(newName);

    // resolve collisions
    for (int i = 0; i < postponedCollisions.size(); i++) {
      ClassHidesImportedClassUsageInfo collision = (ClassHidesImportedClassUsageInfo)postponedCollisions.get(i);
      collision.resolveCollision();
    }
    listener.elementRenamed(aClass);
  }

  private static void doRenameMethod(PsiMethod method,
                                     String newName,
                                     UsageInfo[] usages,
                                     RefactoringElementListener listener) throws IncorrectOperationException {
    // do actual rename of overriding/implementing methods and of references to all them
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (!usage.getElement().isValid()) {
        continue;
      }
      if (!(usage.getElement() instanceof PsiMethod)) {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = ((MoveRenameUsageInfo) usage).reference;
        }
        else {
          ref = usage.getElement().getReference();
        }
        if (ref != null) {
          ref.handleElementRename(newName);
        }
      }
    }
    
    // do actual rename of method
    method.setName(newName);
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (!usage.getElement().isValid()) {
        continue;
      }
      if (usage.getElement() instanceof PsiMethod) {
        ((PsiMethod)usage.getElement()).setName(newName);
      }
    }
    listener.elementRenamed(method);
  }

  private static void doRenameVariable(PsiVariable variable,
                                       String newName,
                                       UsageInfo[] usages,
                                       RefactoringElementListener listener) throws IncorrectOperationException {
    // rename all references
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (!usage.getElement().isValid()) {
        continue;
      }

      if (!(usage instanceof LocalHidesFieldUsageInfo)) {
        final PsiReference ref;
        if (!(usage instanceof MoveRenameUsageInfo)) {
          ref = usage.getElement().getReference();
        }
        else {
          ref = ((MoveRenameUsageInfo)usage).reference;
        }
        if (ref != null) {
          PsiElement newElem = ref.handleElementRename(newName);
          if (variable instanceof PsiField) {
            fixPossibleNameCollisionsForFieldRenaming((PsiField)variable, newName, newElem);
          }
        }
      }
      else {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)usage.getElement();
        PsiElement resolved = collidingRef.resolve();

        if (resolved instanceof PsiField) {
          qualifyField((PsiField)resolved, collidingRef, newName);
        }
        else {
          // do nothing
        }
      }
    }
    // do actual rename
    //PsiIdentifier newNameIdentifier = factory.createIdentifier(newName);
    //variable.getNameIdentifier().replace(newNameIdentifier);
    variable.setName(newName);
    listener.elementRenamed(variable);
  }


  private static void fixPossibleNameCollisionsForFieldRenaming(PsiField field, String newName,
                                                                PsiElement replacedOccurence)
    throws IncorrectOperationException {
    if (!(replacedOccurence instanceof PsiReferenceExpression)) return;
    PsiElement elem = ((PsiReferenceExpression)replacedOccurence).resolve();

    if (elem == null) {
      // If reference is unresolved, then field is not hidden by anyone...
      return;
    }

    if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter) {
      qualifyField(field, replacedOccurence, newName);
    }
  }

  private static void qualifyField(PsiField field, PsiElement occurence, String newName)
    throws IncorrectOperationException {
    PsiManager psiManager = occurence.getManager();
    PsiElementFactory factory = psiManager.getElementFactory();
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      PsiReferenceExpression qualified =
        (PsiReferenceExpression)factory.createExpressionFromText("this." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified =
        (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(
        factory.createReferenceExpression(field.getContainingClass()));
      occurence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createFieldReference(PsiField field, PsiElement context) throws IncorrectOperationException {
    final PsiManager manager = field.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    final String name = field.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    if (manager.areElementsEquivalent(resolved, field)) return ref;
    final PsiJavaCodeReferenceElement qualifier;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
      resolved = ref.resolve();
      if (manager.areElementsEquivalent(resolved, field)) return ref;
      ref = (PsiReferenceExpression) factory.createExpressionFromText("A.this." + name, null);
      qualifier = ((PsiThisExpression)ref.getQualifierExpression()).getQualifier();
      final PsiClass containingClass = field.getContainingClass();
      final PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(containingClass);
      qualifier.replace(classReference);
    } else {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiReferenceExpression)ref.getQualifierExpression();
      final PsiClass containingClass = field.getContainingClass();
      final PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
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

  public static String[] getConflictDescriptions(UsageInfo[] usages) {
    ArrayList<String> descriptions = new ArrayList<String>();

    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];

      if (usage instanceof UnresolvableCollisionUsageInfo) {
        descriptions.add(((UnresolvableCollisionUsageInfo)usage).getDescription());
      }

    }
    return descriptions.toArray(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public static void buildMultipleDirectoriesInPackageMessage(StringBuffer message,
                                                        PsiPackage aPackage,
                                                        PsiDirectory[] directories) {
    message.append("Multiple directories correspond to package\n");
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
