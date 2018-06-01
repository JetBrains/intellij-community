package circlet.klogging.impl

import com.intellij.openapi.diagnostic.*

class ExplicitLogger(logger: Logger) : ApplicationLogger(logger) {
    override fun error(message: Any?) {
        logger.error(message.toString())
    }

    override fun error(t: Throwable, message: Any?) {
        logger.error(message.toString(), t)
    }
}
