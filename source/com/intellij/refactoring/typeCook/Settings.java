package com.intellij.refactoring.typeCook;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 30.12.2003
 * Time: 19:25:26
 * To change this template use Options | File Templates.
 */
public interface Settings {
  boolean dropObsoleteCasts();
  boolean preserveRawArrays();
  boolean leaveObjectParameterizedTypesRaw();
  boolean exhaustive();
  boolean cookObjects();
  boolean cookToWildcards();
}
