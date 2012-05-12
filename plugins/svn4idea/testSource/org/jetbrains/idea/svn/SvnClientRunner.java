/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/2/12
 * Time: 5:44 PM
 */
public interface SvnClientRunner {
  ProcessOutput runSvn(final VirtualFile file, String... commandLine) throws IOException;

  void checkin(final VirtualFile file) throws IOException;

  void update(final VirtualFile file) throws IOException;

  void checkout(String repoUrl, VirtualFile file) throws IOException;

  void add(VirtualFile root, String path) throws IOException;

  void delete(VirtualFile root, String path) throws IOException;

  void copy(VirtualFile root, String path, String from) throws IOException;

  void testSvnVersion(VirtualFile root) throws IOException;
}
