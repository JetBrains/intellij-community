/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.Iterator;

/**
 * @author dsl
 */
public class ConvertingIterator <Domain, Range> implements Iterator<Range> {
  private final Iterator<Domain> myBaseIterator;
  private final Convertor<Domain, Range> myConvertor;

  public static class IdConvertor <T> implements Convertor<T, T> {
    public T convert(T object) {
      return object;
    }
  }

  public ConvertingIterator(Iterator<Domain> baseIterator, Convertor<Domain, Range> convertor) {
    myBaseIterator = baseIterator;
    myConvertor = convertor;
  }

  public boolean hasNext() {
    return myBaseIterator.hasNext();
  }

  public Range next() {
    return myConvertor.convert(myBaseIterator.next());
  }

  public void remove() {
    myBaseIterator.remove();
  }

  public static <Domain, Intermediate, Range> Convertor<Domain, Range> composition(final Convertor<Domain, Intermediate> convertor1,
                                                                                   final Convertor<Intermediate, Range> convertor2) {
    return new Convertor<Domain, Range>() {
      public Range convert(Domain domain) {
        return convertor2.convert(convertor1.convert(domain));
      }
    };
  }

  public static <Domain, Range> ConvertingIterator<Domain, Range>
    create(Iterator<Domain> iterator, Convertor<Domain, Range> convertor) {
    return new ConvertingIterator<Domain, Range>(iterator, convertor);
  }
}
