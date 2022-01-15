package com.jetbrains.packagesearch.intellij.plugin

import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import java.util.stream.Stream

/** Returns an instance of [ArgumentsProvider] from all arguments to this function.  */
internal inline fun <reified T : Any> arguments(vararg args: T): ArgumentsProvider = ArgumentsProvider {
    Stream.of(*args).map { Arguments.of(it) }
}

internal fun String?.asNullable() = if (this == "%%NULL%%") null else this
