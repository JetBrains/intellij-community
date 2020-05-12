package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.*

class SystemTimeTicker : Ticker {
    override val transactionTime: Long get() = System.currentTimeMillis()
}
