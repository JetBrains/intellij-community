// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import kotlin.system.exitProcess

internal fun exitWithErrorMessage(message: String, exitCode: Int = 1): Nothing {
    System.err.println(message)
    exitProcess(exitCode)
}

internal fun GeneratorPreferences.getTicketAndCheck(): String {
    val ticket = ticket ?: run {
        println("No '${GeneratorPreferences::ticket.name}' preference is provided; entering interactive mode...")
        print("Enter YouTrack ticket: ")
        readln()
    }

    if (!ticket.matches(Regex("KTIJ-\\d+"))) {
        exitWithErrorMessage("Mandatory YouTrack ticket is not a valid KTIJ-xxxx ticket")
    }
    return ticket
}