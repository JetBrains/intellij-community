package de.plushnikov.delegate.issue88.visitor;

public interface Visitor {
  <T> T visit(T object);
}