package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Obsolete
@JvmInline
value class ShellName(val name: String)

@ApiStatus.Obsolete
fun ShellName.isZsh(): Boolean = name == "zsh"

@ApiStatus.Obsolete
fun ShellName.isBash(): Boolean = name == "bash"

@ApiStatus.Obsolete
fun ShellName.isPowerShell(): Boolean = name == "powershell"