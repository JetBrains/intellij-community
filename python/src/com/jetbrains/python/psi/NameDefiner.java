package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * PSI element that (re)defnies names in following namespace, e.g. assignment statement does.
 * User: dcheryasov
 * Date: Jul 3, 2008
 */
public interface NameDefiner extends PsiElement {
  /**
   * @return an iterator that iterates over defined names, in order of definition.
   * Complex targets count, too: "(y, x[1]) = (1, 2)" return both "y" and "x[1]".
   */
  @NotNull
  Iterable<PyElement> iterateNames();

  /**
   * @param the_name an unqualified name.
   * @return an element which is defined under that name in this instance, or null. 
   */
  @Nullable
  PsiElement getElementNamed(String the_name);

  /**
   * @return true if names found inside its children cannot be resolved to names defined by this statement..
   * E.g. name <tt>a</tt> is defined in statement <tt>a = a + 1</tt> but the <tt>a</tt> on the left hand side
   * must not resolve to the <tt>a</tt> on the right hand side.
   */
  boolean mustResolveOutside();


  /**
   * Convenience iterator wrapper; makes items cast to given type (presumably PyExpression).
   * @param <T> class to cast to.
   */
  class ArrayIterable<T> implements Iterable<T> {
    protected T[] mySource;
    public ArrayIterable(T[] source){
      mySource = source;
    }

    public Iterator<T> iterator() {
      return new ArrayIter<T>(mySource);
    }
  }

  class ArrayIter<T> implements Iterator<T> {

    protected int my_index;
    protected T[] content;

    public ArrayIter(T[] content) {
      this.content = content;
      my_index = 0;
    }

    public boolean hasNext() {
      return ((content != null) && (my_index < content.length));
    }

    public T next() {
      if (hasNext()) {
        T ret = content[my_index];
        my_index +=  1;
        return ret;
      }
      else throw new NoSuchElementException(content == null ? "Null content" : "Only got " + content.length + "items");
    }

    public void remove() {
      throw new UnsupportedOperationException("Can't remove targets from iter");
    }
  }

  class SingleIterable<T> implements Iterable<T> {

    T content;
    public SingleIterable(T content) {
      this.content = content;
    }

    public Iterator<T> iterator() {
      return new SingleIter(); 
    }

    class SingleIter implements Iterator<T> {

      boolean expired;
      SingleIter() {
        expired = false;
      }
      public boolean hasNext() {
        return !expired;
      }

      public T next() {
        if (hasNext()) {
          expired = true;
          return content;
        }
        else throw new NoSuchElementException("Single iter expired");
      }

      public void remove() {
        throw new UnsupportedOperationException("Can't remove targets from single iter");
      }
    }
  }

  class IterHelper {
    private IterHelper() {}
    @Nullable
    public static PyElement findName(Iterable<PyElement> it, String name) {
      PyElement ret = null;
      for (PyElement elt : it) {
        if ((elt != null) && (name.equals(elt.getName()))) {
          ret = elt;
          break;
        }
      }
      return ret;
    }
  }

}
