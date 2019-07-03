package circlet.plugins.pipelines.services.execution

import libraries.common.*

class SystemTimeTicker : Ticker {
    override val now: Long get() = System.currentTimeMillis()
}
