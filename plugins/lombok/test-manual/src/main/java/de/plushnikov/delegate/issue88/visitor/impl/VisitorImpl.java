package de.plushnikov.delegate.issue88.visitor.impl;

import de.plushnikov.delegate.issue88.visitor.Visitor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VisitorImpl implements Visitor {
  @Override
  public <T> T visit(T object) {
    log.debug("Lets see what we got: {}", object.getClass());
    return object;
  }
}