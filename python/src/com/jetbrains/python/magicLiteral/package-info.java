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
 * Magic literals are literals that could be used as reference targets.
 * There are some references that resolve to literals.
 * Find Usage and Rename should work for them.
 * This package provides full support for such literals allowing developers to "inject" special knowledge via extension points.
 * To add new "magic literal" just implement {@link com.jetbrains.python.magicLiteral.PyMagicLiteralExtensionPoint} and
 * add it as extension point.
 * To check for some literal if it is magic, use {@link com.jetbrains.python.magicLiteral.PyMagicLiteralTools}
 * or obtain extension point directly (tools class simplifies usages).
 *
 * Several components and extension point implementations exist in this package. Be sure to install all of them to make
 * this package work.
 * After that, you only need to implement and inject  {@link com.jetbrains.python.magicLiteral.PyMagicLiteralTools}
 *
 *
 *
 * <p>
 *   <h2>1. To make your app support this package</h2>
 *   Be sure to read all classes in this package and install them to proper places.
 *   For example, package would not work with out of {@link com.jetbrains.python.magicLiteral.PyMagicLiteralReferenceSearcher}
 *   installed
 * </p>
 * <p>
 *   <h2>2. To support new magic literal</h2>
 *   Say, you know that literal assigned to variable named "spam" is called "spam literal" and some references around the
 *   code are resolved to it. You want "find usage" and "rename" work for it.
 *   Just implement {@link com.jetbrains.python.magicLiteral.PyMagicLiteralExtensionPoint} and inject it as extension point.
 *   <strong>Warning</strong>: install this package as described in first step
 * </p>
 * <p>
 *   <h2>3. To check if literal is magic</h2>
 *   Say, you have some {@link com.jetbrains.python.psi.PyStringLiteralExpression} and want to check if it is magic or not,
 *   and if it is -- find its name ("spam literal" for previous example).
 *   You use {@link com.jetbrains.python.magicLiteral.PyMagicLiteralTools} for that, because may magic literal extension points
 *   could be installed.
 *   <strong>Warning</strong>: install this package as described in first step
 * </p>
 *
 *
 * @see com.jetbrains.python.magicLiteral.PyMagicLiteralTools
 * @see com.jetbrains.python.magicLiteral.PyMagicLiteralExtensionPoint
 * */
package com.jetbrains.python.magicLiteral;