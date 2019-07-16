package circlet.plugins.pipelines.services.execution

import libraries.common.*

class SystemTimeTicker : Ticker {
    override val transactionTime: Long get() = System.currentTimeMillis()
}
