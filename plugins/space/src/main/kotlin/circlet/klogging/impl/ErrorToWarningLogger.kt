package circlet.klogging.impl

import com.intellij.openapi.diagnostic.*

class ErrorToWarningLogger(logger: Logger) : ApplicationLogger(logger) {
    override fun error(message: Any?) {
        logger.warn(message.toString())
    }

    override fun error(t: Throwable, message: Any?) {
        logger.warn(message.toString(), t)
    }
}
