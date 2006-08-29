/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ObjectNode;
import junit.framework.TestCase;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class DisposerTest extends TestCase {

  protected MyDisposable myRoot;

  protected MyDisposable myFolder1;
  protected MyDisposable myFolder2;

  protected MyDisposable myLeaf1;
  private MyDisposable myLeaf2;

  private List myDisposedObjects = new ArrayList();

  protected void setUp() throws Exception {
    myRoot = new MyDisposable("root");

    myFolder1 = new MyDisposable("folder1");
    myFolder2 = new MyDisposable("folder2");

    myLeaf1 = new MyDisposable("leaf1");
    myLeaf2 = new MyDisposable("leaf2");
  }

  public void testDiposalAndAbsenceOfReferences() throws Exception {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myRoot, myFolder2);
    Disposer.register(myFolder1, myLeaf1);

    Disposer.dispose(myFolder1);
    assertFalse(myRoot.isDisposed());
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
    assertFalse(myFolder2.isDisposed());

    Disposer.dispose(myRoot);
    assertDisposed(myRoot);
    assertDisposed(myFolder2);

    Disposer.dispose(myLeaf1);
  }

  public void testDisposalOrder() throws Exception {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myRoot, myFolder2);

    Disposer.dispose(myRoot);

    List expected = new ArrayList();
    expected.add(myFolder2);
    expected.add(myLeaf1);
    expected.add(myFolder1);
    expected.add(myRoot);

    assertEquals(expected, myDisposedObjects);
  }

  public void testDirectCallOfDisposable() throws Exception {
    SelDisposable selfDisposable = new SelDisposable("root");
    Disposer.register(myRoot, selfDisposable);
    Disposer.register(selfDisposable, myFolder1);
    Disposer.register(myFolder1, myFolder2);

    selfDisposable.dispose();

    assertDisposed(selfDisposable);
    assertDisposed(myFolder1);
    assertDisposed(myFolder2);

    assertEquals(0, Disposer.getTree().getExecutedObjects().size());
  }

  public void testDisposeAndReplace() throws Exception {
    Disposer.register(myRoot, myFolder1);

    Disposer.disposeChildAndReplace(myFolder1, myFolder2);
    assertDisposed(myFolder1);

    Disposer.dispose(myRoot);
    assertDisposed(myRoot);
    assertDisposed(myFolder2);
  }

  public void testPostponedParentRegistration() throws Exception {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myLeaf1, myLeaf2);
    Disposer.register(myRoot, myFolder1);


    Disposer.dispose(myRoot);

    assertDisposed(myRoot);
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testDisposalOfParentless() throws Throwable {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder1, myFolder2);
    Disposer.register(myFolder2, myLeaf2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testDisposalOfParentess2() throws Throwable {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder2, myLeaf2);
    Disposer.register(myFolder1, myFolder2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testOverrideParentDisposable() throws Exception {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder2, myFolder1);
    Disposer.register(myRoot, myFolder1);

    Disposer.dispose(myFolder2);

    assertDisposed(myFolder2);
    assertFalse(myLeaf1.isDisposed());
    assertFalse(myFolder1.isDisposed());

    Disposer.dispose(myRoot);
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
  }

  public void testDisposerDoesntHandleReferenceToChildObject() throws Exception {
    Disposable root = new MyDisposable("root");
    Disposable child = new MyDisposable("child");


    Disposer.register(root, child);

    child = null;

    Reference r = new WeakReference<Disposable>(child);
    System.gc();
    System.gc();
    System.gc();

    assertNull(r.get());
    assertEquals("[child]", myDisposedObjects.toString());
  }

  private void assertDisposed(MyDisposable aDisposable) {
    assertTrue(aDisposable.isDisposed());
    assertFalse(aDisposable.toString(), Disposer.getTree().getObject2NodeMap().containsKey(aDisposable));

    Collection nodes = Disposer.getTree().getObject2NodeMap().values();
    for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
      assertNoReferenceKeptInTree((ObjectNode)iterator.next(), aDisposable);
    }

  }

  private void assertNoReferenceKeptInTree(ObjectNode aNode, MyDisposable aDisposable) {
    assertNotSame(aNode.getObject(), aDisposable);
    final List children = aNode.getChildren();
    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        assertNoReferenceKeptInTree((ObjectNode)children.get(i), aDisposable);
      }
    }
  }

  private class MyDisposable implements Disposable {

    private boolean myDisposed = false;
    private String myName;

    public MyDisposable(String aName) {
      myName = aName;
    }

    public void dispose() {
      myDisposed = true;
      myDisposedObjects.add(this);
    }

    public boolean isDisposed() {
      return myDisposed;
    }

    public String toString() {
      return myName;
    }
  }

  private class SelDisposable extends MyDisposable {
    public SelDisposable(String aName) {
      super(aName);
    }

    public void dispose() {
      Disposer.dispose(this);
      super.dispose();
    }
  }
}
