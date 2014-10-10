/*******************************************************************************
 * Copyright (c) 2000, 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/

package com.jetbrains.python.internal;

import com.google.common.base.Joiner;

/**
 * @author traff
 */
public class PyProcessInfo {

  private final int myPid;
  private final String myCommand;
  private final String myArgs;

  public PyProcessInfo(String pidString, String name) {
    this(Integer.parseInt(pidString), name);
  }

  public PyProcessInfo(int pid, String name) {
    myPid = pid;
    String[] args = name.split(" ");
    myCommand = args.length > 0 ? args[0] : "";
    myArgs = name;
  }

  public int getPid() {
    return myPid;
  }

  public String getCommand() {
    return myCommand;
  }

  @Override
  public String toString() {
    return Joiner.on("").join(String.valueOf(myPid), " (", myArgs, ")");
  }

  public String getArgs() {
    return myArgs;
  }
}