// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * <h1>Command line console</h1>
 * <p>
 * Package to display command line console on the Terminal ToolWindow. It supports history, execution, syntax highlighting and so on.
 * Entry point is {@link com.jetbrains.python.commandInterfaceConsole.CommandLineConsoleApiKt}.
 * </p>
 * <p/>
 * <h2>Technical details</h2>
 * <p>
 * This package uses {@link com.jetbrains.python.commandInterfaceConsole.CommandConsole} and it can work in 2 modes: "command-mode"
 *  to accept, highlight and execute commands, and in "process-mode" to pass stdin/stdout to process it execute.
 * See class documentation for details.
 * </p>
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.commandInterfaceConsole;