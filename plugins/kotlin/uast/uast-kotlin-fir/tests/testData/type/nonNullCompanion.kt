package test.pkg

class Issue {
    companion object {
        @JvmStatic
        fun create(
            id: String,
            briefDescription: String,
            explanation: String
        ): Issue {
            return Issue()
        }
    }
}