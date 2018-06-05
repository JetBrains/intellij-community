package circlet.klogging.impl

import com.intellij.openapi.diagnostic.*
import klogging.impl.*
import kotlin.reflect.*

abstract class ApplicationKLoggers : KLoggerFactory {
    override fun logger(owner: KClass<*>): KLogger = wrapLogger(Logger.getInstance(owner.java))

    override fun logger(owner: Any): KLogger = logger(owner.javaClass.kotlin)

    override fun logger(name: String): KLogger = wrapLogger(Logger.getInstance(name))

    private fun wrapLogger(logger: Logger) = KLogger(wrapWithApplicationLogger(logger))

    protected abstract fun wrapWithApplicationLogger(logger: Logger): ApplicationLogger
}
