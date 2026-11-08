package test

interface EditorThreading {
    companion object {
        @JvmStatic
        fun <T, E : Throwable> compute(action: ThrowableComputable<T, E>): T = action.compute()
    }
}
