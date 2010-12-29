/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml;

/**
 * Visitor is a very common design pattern. DOM model also has a visitor and it's called
 * DomElementVisitor. The {@link DomElement} interface has methods {@link DomElement#accept(DomElementVisitor)}
 * and {@link DomElement#acceptChildren(DomElementVisitor)}
 * taking this visitor as a parameter. 
 * <p>
 * DomElementVisitor has only one method: {@link #visitDomElement(DomElement)}.
 * Where is the Visitor pattern? Where are all those methods with names like <code>visitT(T)</code> that
 * are usually found in it? There are no such methods, because the actual interfaces (<code>T</code>'s)
 * aren't known to anyone except you. But when you instantiate the DomElementVisitor
 * interface, you may add your own <code>visitT()</code> methods and they will be called! You may
 * even name them just <code>visit()</code>, specify the type of the parameter and everything will be
 * fine. 
 * <p>
 * For example, if you have two DOM element classes - <code>Foo</code> and <code>Bar</code>- your visitor
 * may look like this:
 * <pre>
 *  class MyVisitor implements DomElementVisitor {
 *    void visitDomElement(DomElement element) {}
 *    void visitFoo(Foo foo) {}
 *    void visitBar(Bar bar) {}
 *  }
 * </pre>
 * @author peter
 */
public interface DomElementVisitor {
  void visitDomElement(DomElement element);
}
