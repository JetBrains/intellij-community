package circlet.klogging.impl

import com.intellij.openapi.diagnostic.*

object ErrorToWarningKLoggers : ApplicationKLoggers() {
    override fun wrapWithApplicationLogger(logger: Logger): ApplicationLogger = ErrorToWarningLogger(logger)
}
