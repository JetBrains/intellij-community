/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

@SuppressWarnings({"HardCodedStringLiteral"})
public interface SvnPropertyKeys {
  String SVN_EOL_STYLE = "svn:eol-style";
  String SVN_KEYWORDS = "svn:keywords";
  String SVN_NEEDS_LOCK = "svn:needs-lock";
  String SVN_MIME_TYPE = "svn:mime-type";
  String SVN_EXECUTABLE = "svn:executable";
  String SVN_IGNORE = "svn:ignore";
  String SVN_EXTERNALS = "svn:externals";
  String LOG = "svn:log";
  String MERGE_INFO = "svn:mergeinfo";
}
