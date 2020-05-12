package circlet.klogging.impl

import com.intellij.openapi.diagnostic.*
import libraries.klogging.*
import kotlin.reflect.*

object KLoggerFactoryIdea : KLoggerFactory {
    override fun logger(owner: KClass<*>): KLogger = wrapLogger(Logger.getInstance(owner.java))

    override fun logger(owner: Any): KLogger = logger(owner.javaClass.kotlin)

    override fun logger(name: String): KLogger = wrapLogger(Logger.getInstance(name))

    override fun logger(nameSource: LoggerNameSource): KLogger = logger(loggerNameFromSource(nameSource))

    private fun wrapLogger(logger: Logger) = KLogger(wrapWithApplicationLogger(logger))

    fun wrapWithApplicationLogger(logger: Logger): ApplicationLogger = ApplicationLogger(logger)
}
