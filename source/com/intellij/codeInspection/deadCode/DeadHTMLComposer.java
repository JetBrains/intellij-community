/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2001
 * Time: 4:58:38 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.HTMLComposer;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.*;

import java.util.Iterator;

public class DeadHTMLComposer extends HTMLComposer {
  private final InspectionTool myTool;

  public DeadHTMLComposer(InspectionTool tool) {
    myTool = tool;
  }

  public void compose(final StringBuffer buf, RefEntity refEntity) {
    genPageHeader(buf, refEntity);

    if (refEntity instanceof RefElement) {
      RefElement refElement = (RefElement) refEntity;
      if (refElement.isSuspicious() && !refElement.isEntry()) {
        appendHeading(buf, "Problem synopsis");
        buf.append("<br>");
        appendAfterHeaderIndention(buf);
        appendProblemSynopsis(refElement, buf);

        buf.append("<br><br>");
        appendResolution(buf, myTool, refElement);

        refElement.accept(new RefVisitor() {
          public void visitClass(RefClass aClass) {
            appendClassInstantiations(buf, aClass);
            appendDerivedClasses(buf, aClass);
            appendClassExtendsImplements(buf, aClass);
            appendLibraryMethods(buf, aClass);
            appendTypeReferences(buf, aClass);
          }

          public void visitMethod(RefMethod method) {
            appendElementInReferences(buf, method);
            appendElementOutReferences(buf, method);
            appendDerivedMethods(buf, method);
            appendSuperMethods(buf, method);
          }

          public void visitField(RefField field) {
            appendElementInReferences(buf, field);
            appendElementOutReferences(buf, field);
          }
        });
      } else {
        appendNoProblems(buf);
      }
    }
  }

  public void appendProblemSynopsis(final RefElement refElement, final StringBuffer buf) {
    refElement.accept(new RefVisitor() {
      public void visitField(RefField field) {
        if (field.isUsedForReading() && !field.isUsedForWriting()) {
          buf.append("Field is never assigned.");
          return;
        }

        if (!field.isUsedForReading() && field.isUsedForWriting()) {
          if (field.isOnlyAssignedInInitializer()) {
            buf.append("Field has no usages.");
            return;
          }

          buf.append("Field is assigned but never accessed.");
          return;
        }

        int nUsages = field.getInReferences().size();
        if (nUsages == 0) {
          buf.append("Field has no usages.");
        } else if (nUsages == 1) {
          buf.append("Field has one usage but it is not reachable from entry points.");
        } else {
          buf.append("Field has ");
          appendNumereable(buf, nUsages, "usage", "", "s");
          buf.append(" but they are not reachable from entry points.");
        }
      }

      public void visitClass(RefClass refClass) {
        if (refClass.isAnonymous()) {
          buf.append("Anonymous class declaration context is not reachable from entry points. Class is never instantiated.");
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          appendClassOrInterface(buf, refClass, true);
          buf.append("&nbsp;");

          int nDerived = getImplementationsCount(refClass);

          if (nDerived == 0) {
            buf.append("is not implemented.");
          } else if (nDerived == 1) {
            buf.append("has an implementation but <ul><li>it is never instantiated OR</li><li>no instantiations are reachable from entry points.</li></ul>");
          } else {
            buf.append("has ");
            appendNumereable(buf, nDerived, "direct or indirect implementation", "", "s");
            buf.append(" but <ul><li>they are never instantiated OR</li><li>no instantiations are reachable from entry points.</li></ul>");
          }
        } else if (refClass.isUtilityClass()) {
          buf.append("No class references has been found. Class static initializer is not reachable.");
        } else {
          int nInstantiationsCount = getInstantiationsCount(refClass);

          if (nInstantiationsCount == 0) {
            int nImplementations = getImplementationsCount(refClass);
            if (nImplementations != 0) {
              buf.append("Neither the class nor ");
              appendNumereable(buf, nImplementations, " its implementation", "", "s");
              buf.append(" are ever instantiated.");
            } else {
              buf.append("Class is not instantiated.");
            }
          } else if (nInstantiationsCount == 1) {
            buf.append("Class has one instantiation but it is not reachable from entry points.");
          } else {
            buf.append("Class has ");
            appendNumereable(buf, nInstantiationsCount, "instantiation", "", "s");
            buf.append(" but they are not reachable from entry points.");
          }
        }
      }

      public void visitMethod(RefMethod method) {
        RefClass refClass = method.getOwnerClass();

        if (method.isLibraryOverride()) {
          buf.append("Method overrides a library method but<ul><li>its ");
          appendClassOrInterface(buf, refClass, false);
          buf.append(" is never instantiated OR</li><li>its");
          appendClassOrInterface(buf, refClass, false);
          buf.append(" instantiation is not reachable from entry points.</li></ul>");
        } else if (method.isStatic() || method.isConstructor()) {
          buf.append(method.isConstructor() ? "Constructor " : "Method ");
          int nRefs = method.getInReferences().size();
          if (nRefs == 0) {
            buf.append("is never used.");
          } else if (method.isConstructor() && method.isSuspiciousRecursive()) {
            buf.append("has usage(s) but they all belong to recursive calls chain that has no members reachable from entry points.");
          } else if (nRefs == 1) {
            buf.append("has one usage but it is not reachable from entry points.");
          } else {
            buf.append("has ");
            appendNumereable(buf, nRefs, "usage", "", "s");
            buf.append(" usages but they are not reachable from entry points.");
          }
        } else if (refClass.isSuspicious()) {
          if (method.isAbstract()) {
            buf.append("<ul><li>Abstract method is not implemented OR</li>");
            buf.append("<li>Implementation class is never instantiated OR</li>");
            buf.append("<li>An instantiation is not reachable from entry points.</li></ul>");
          } else {
            buf.append("<ul><li>Method owner class is never instantiated OR</li>");
            buf.append("<li>An instantiation is not reachable from entry points.</li></ul>");
          }
        } else {
          int nOwnRefs = method.getInReferences().size();
          int nSuperRefs = getSuperRefsCount(method);
          int nDerivedRefs = getDerivedRefsCount(method);

          if (nOwnRefs == 0 && nSuperRefs == 0 && nDerivedRefs == 0) {
            buf.append("Method is never used.");
          } else if (nDerivedRefs > 0 && nSuperRefs == 0 && nOwnRefs == 0) {
            buf.append("Method is never used as a member of this ");
            appendClassOrInterface(buf, refClass, false);
            buf.append(", but only as a member of the implementation class(es).");
            buf.append("The project will stay compilable if the method is removed from the ");
            appendClassOrInterface(buf, refClass, false);
            buf.append(".");
          } else if (method.isSuspiciousRecursive()) {
            buf.append("Method has usage(s) but they all belong to recursive calls chain that has no members reachable from entry points.");
          } else {
            buf.append("Method is not reachable from entry points.");
          }
        }
      }
    });
  }

  protected void appendAdditionalListItemInfo(StringBuffer buf, RefElement refElement) {
    if (refElement instanceof RefImplicitConstructor) {
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }

    buf.append("<br><font style=\"font-family:verdana;color:#808080\">");
    if (refElement instanceof RefClass) {
      RefClass refClass = (RefClass) refElement;
      if (refClass.isUtilityClass()) {
        // Append nothing.
      } else if (refClass.isAnonymous()) {
        buf.append(refClass.isSuspicious() ? "Anonymous class context is not reachable. Class is not instantiated." : "Instantiated");
      } else if (refClass.isInterface() || refClass.isAbstract()) {
        buf.append(refClass.isSuspicious() ? "Has no reachable implementation instantiations. " : "Has reachable implementation instantiations.");
      } else {
        buf.append(refClass.isSuspicious() ? "Has no reachable instantiations. " : "Has reachable instantiations. ");
      }

      appendNumereable(buf, getInstantiationsCount(refClass), "instantiation", "", "s");
      buf.append(" found in the project code.");
    } else {
      buf.append(refElement.isSuspicious() ? "Not Reachable. " : "Reachable. ");
      int nUsageCount = refElement.getInReferences().size();
      if (refElement instanceof RefMethod) {
        nUsageCount += getDerivedRefsCount((RefMethod) refElement);
      }
      buf.append(nUsageCount);
      buf.append(" usages found in the project code.");
    }

    buf.append("</font>");
  }

  private int getDerivedRefsCount(RefMethod refMethod) {
    int count = 0;

    for (Iterator<RefMethod> iterator = refMethod.getDerivedMethods().iterator(); iterator.hasNext();) {
      RefMethod refDerived = iterator.next();
      count += refDerived.getInReferences().size() + getDerivedRefsCount(refDerived);
    }

    return count;
  }

  private int getSuperRefsCount(RefMethod refMethod) {
    int count = 0;

    for (Iterator<RefMethod> iterator = refMethod.getSuperMethods().iterator(); iterator.hasNext();) {
      RefMethod refSuper = iterator.next();
      count += refSuper.getInReferences().size() + getSuperRefsCount(refSuper);
    }

    return count;
  }

  private static int getInstantiationsCount(RefClass aClass) {
    if (!aClass.isAnonymous()) {
      int count = 0;

      for (Iterator<RefMethod> iterator = aClass.getConstructors().iterator(); iterator.hasNext();) {
        RefMethod refConstructor = iterator.next();
        count += refConstructor.getInReferences().size();
      }

      for (Iterator<RefClass> iterator = aClass.getSubClasses().iterator(); iterator.hasNext();) {
        RefClass subClass = iterator.next();
        count += getInstantiationsCount(subClass);
        count -= subClass.getConstructors().size();
      }

      return count;
    }

    return 1;
  }

  private static int getImplementationsCount(RefClass refClass) {
    int count = 0;
    for (Iterator<RefClass> iterator = refClass.getSubClasses().iterator(); iterator.hasNext();) {
      RefClass subClass = iterator.next();
      if (!subClass.isInterface() && !subClass.isAbstract()) {
        count++;
      }
      count += getImplementationsCount(subClass);
    }

    return count;
  }

  private void appendClassInstantiations(StringBuffer buf, RefClass refClass) {
    if (!refClass.isInterface() && !refClass.isAbstract() && !refClass.isUtilityClass()) {
      boolean found = false;

      appendHeading(buf, "Instantiated from");

      startList();
      for (Iterator<RefMethod> iterator = refClass.getConstructors().iterator(); iterator.hasNext();) {
        RefMethod refMethod = iterator.next();
        for (Iterator<RefElement> constructorCallersIterator = refMethod.getInReferences().iterator(); constructorCallersIterator.hasNext();) {
          RefElement refCaller = constructorCallersIterator.next();
          appendListItem(buf, refCaller);
          found = true;
        }
      }

      if (!found) {
        startListItem(buf);
        buf.append("No instantiations found.");
        doneListItem(buf);
      }

      doneList(buf);
    }
  }
}
