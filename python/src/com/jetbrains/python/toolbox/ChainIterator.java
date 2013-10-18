package com.jetbrains.python.toolbox;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * An iterator that combines several other iterators and exhaust them one by one, in chain.
 * User: dcheryasov
 * Date: Nov 19, 2009 3:49:38 AM
 */
public class ChainIterator<T> extends ChainedListBase<Iterator<T>> implements Iterator<T> {

  private ChainIterationMixin<T, Iterator<T>> myMixin;

  /**
   * Creates new instance.
   * @param initial initial iterator. If null, the new iterator is empty, use {@link #add} to add initial content.
   */
  public ChainIterator(@Nullable Iterator<T> initial) {
    super(initial);

    myMixin = new ChainIterationMixin<T, Iterator<T>>(this) {
      @Override
      public Iterator<T> toIterator(Iterator<T> first) {
        return first;
      }
    };
  }


  /**
   * Adds another iterator to the chain. Values from this iterator will follow the values of the iterator passed to the constructor.
   * Adding after the iteration has started is safe. 
   * @param another iterator to add to the end of the chain.
   * @return self, for easy chaining.
   */
  public ChainIterator<T> add(Iterator<T> another) {
    return (ChainIterator<T>)super.add(another);
  }


  public boolean hasNext() {
    return myMixin.hasNext();
  }

  public T next() {
    return myMixin.next();
  }

  public void remove() {
    throw new UnsupportedOperationException("Cannot remove from ChainIterator");
  }
}
