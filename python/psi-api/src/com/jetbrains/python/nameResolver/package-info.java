/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * Some code is bound to specific functions or classes or some other objects somewhere in namespace.
 * Say, you want to add reference contributor to "my.package.some_function". You can not just check "callee text" here, because this
 * function could be imported "from my.package import some_function as foo" and then used as "foo". So, you need to resolve it first.
 *
 * Additionally, there are some functions which should be processed identically. patterns() and i18n_patterns() from django is good example.
 * So, you need to:
 * <ol>
 *   <li>Create some enum that implements {@link com.jetbrains.python.nameResolver.FQNamesProvider} (see its javadoc for more info)</li>
 *   <li>Use {@link com.jetbrains.python.nameResolver.NameResolverTools#isName(com.jetbrains.python.psi.PyElement, FQNamesProvider)}
 *   to check that some element matches some names from that enum</li>
 * </ol>
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.nameResolver;