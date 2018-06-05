package circlet.klogging.impl

import com.intellij.openapi.diagnostic.*

object ExplicitKLoggers : ApplicationKLoggers() {
    override fun wrapWithApplicationLogger(logger: Logger): ApplicationLogger = ExplicitLogger(logger)
}
