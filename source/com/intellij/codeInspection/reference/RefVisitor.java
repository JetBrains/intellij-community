/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 10:13:46 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

public class RefVisitor {
  public void visitElement(RefElement elem) {

  }

  public void visitField(RefField field) {
    visitElement(field);
  }

  public void visitMethod(RefMethod method) {
    visitElement(method);
  }

  public void visitParameter(RefParameter parameter) {
    visitElement(parameter);
  }

  public void visitClass(RefClass aClass) {
    visitElement(aClass);
  }
}
