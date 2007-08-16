/*
 * @author max
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MergeQuery<T, T1 extends T, T2 extends T> implements Query<T>{
  private Query<T1> myQuery1;
  private Query<T2> myQuery2;

  public MergeQuery(final Query<T1> query1, final Query<T2> query2) {
    myQuery1 = query1;
    myQuery2 = query2;
  }

  @NotNull
  public Collection<T> findAll() {
    List<T> results = new ArrayList<T>();
    results.addAll(myQuery1.findAll());
    results.addAll(myQuery2.findAll());
    return results;
  }

  public T findFirst() {
    final T r1 = myQuery1.findFirst();
    if (r1 != null) return r1;
    return myQuery2.findFirst();
  }

  public boolean forEach(@NotNull final Processor<T> consumer) {
    Processor<T1> wrapper1 = new Processor<T1>() {
      public boolean process(final T1 t) {
        return consumer.process(t);
      }
    };
    boolean wantMore = myQuery1.forEach(wrapper1);

    if (wantMore) {
      Processor<T2> wrapper2 = new Processor<T2>() {
        public boolean process(final T2 t) {
          return consumer.process(t);
        }
      };

      wantMore = myQuery2.forEach(wrapper2);
    }

    return wantMore;
  }

  public T[] toArray(final T[] a) {
    final Collection<T> results = findAll();
    return results.toArray(a);
  }

  public Iterator<T> iterator() {
    return findAll().iterator();
  }
}