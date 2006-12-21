/*
 * @author max
 */
package com.intellij.util.containers;

import java.util.ArrayList;
import java.util.EmptyStackException;

public class Stack<T> extends ArrayList<T> {
  public void push(T t) {
    add(t);
  }

  public T peek() {
    final int size = size();
    if (size == 0) throw new EmptyStackException();
    return get(size - 1);
  }

  public T pop() {
    final int size = size();
    if (size == 0) throw new EmptyStackException();
    return remove(size - 1);
  }

  public boolean empty() {
    return size() == 0;
  }
}