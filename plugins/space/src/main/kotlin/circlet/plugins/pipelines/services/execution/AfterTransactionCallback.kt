package circlet.plugins.pipelines.services.execution

//copypasted from server. todo remove
class AfterTransactionCallback {
    private val hooks = mutableListOf<suspend () -> Unit>()

    fun afterTransaction(body: suspend () -> Unit) {
        hooks.add(body)
    }

    suspend fun run() {
        hooks.forEach { it() }
    }
}
