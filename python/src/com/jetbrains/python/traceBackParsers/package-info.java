/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * <h1>A pack of test output parsers.</h1>
 * <p>
 * Each parser knows how to parse test output to fetch file links from stack traces.
 * All parsers should extend {@link com.jetbrains.python.traceBackParsers.TraceBackParser} and be installed to
 * {@link com.jetbrains.python.traceBackParsers.TraceBackParserAdapter#PARSERS} while this functionality has no EP.
 * </p>
 */
package com.jetbrains.python.traceBackParsers;