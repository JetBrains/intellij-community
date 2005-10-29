/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

/**
 * @author max
 */
public interface QueryExecutor<Result, Param> {
  boolean execute(Param queryParameters, Processor<Result> consumer);
}
