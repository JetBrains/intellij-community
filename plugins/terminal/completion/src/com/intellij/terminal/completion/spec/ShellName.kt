package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@JvmInline
value class ShellName(val name: String)

@ApiStatus.Experimental
fun ShellName.isZsh(): Boolean = name == "zsh"

@ApiStatus.Experimental
fun ShellName.isBash(): Boolean = name == "bash"

@ApiStatus.Experimental
fun ShellName.isPowerShell(): Boolean = name == "powershell"