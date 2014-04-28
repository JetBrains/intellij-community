/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.commandLine;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 1:49 PM
 */
public enum SvnCommandName {
  version("--version", false),
  info("info", false),
  st("st", false),
  up("up", true),
  switchCopy("switch", true),
  relocate("relocate", true),
  ci("commit", true),
  checkout("checkout", true),
  cleanup("cleanup", true),
  cat("cat", false),
  add("add", true),
  log("log", false),
  revert("revert", true),
  delete("delete", true),
  copy("copy", true),
  move("move", true),
  resolve("resolve", true),
  propget("propget", false),
  proplist("proplist", false),
  propset("propset", true),
  propdel("propdel", true),
  blame("blame", false),
  merge("merge", true),
  changelist("changelist", true),
  lock("lock", true),
  unlock("unlock", true),
  importFolder("import", false),
  export("export", false),
  upgrade("upgrade", true),
  list("list", false),
  diff("diff", false),
  // currently used to create only folders directly in repository - so writable = false
  mkdir("mkdir", false);

  private final String myName;
  private final boolean myWriteable;

  private SvnCommandName(String name, boolean writeable) {
    myName = name;
    myWriteable = writeable;
  }

  public String getName() {
    return myName;
  }

  public boolean isWriteable() {
    return myWriteable;
  }
}
