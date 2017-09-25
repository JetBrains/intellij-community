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
package org.jetbrains.idea.svn;

import org.jetbrains.idea.svn.networking.SSLProtocolExceptionParser;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLProtocolException;

public class SSLExceptionParserTest {
  @Test
  public void testRealLifeCase() {
    final String original = "handshake alert:  unrecognized_name";
    final SSLProtocolException exception = new SSLProtocolException(original);
    final SSLProtocolExceptionParser parser = new SSLProtocolExceptionParser(exception.getMessage());
    parser.parse();
    final String message = parser.getParsedMessage();
    System.out.println(message);
    Assert.assertNotSame(original, message);
  }
}
