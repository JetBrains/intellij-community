class SomeServiceUsage {
    val service: SomeService
        get() = SomeService.getInstanceNotNull()

    val serviceNullable: SomeService?
        get() = SomeService.getInstanceNullable()

    val serviceNotNullByDataFlow: SomeService
        // elvis
        get() {
            val s = SomeService.getInstanceNullable()
            return if (s == null) SomeService.getInstanceNotNull() else s
        }

    // nullable, bang-bang
    fun aString1(): String? {
        return this.serviceNullable!!.nullableString()
    }

    // nullable
    fun aString2(): String? {
        return this.service.nullableString()
    }

    // not nullable
    fun aString3(): String? {
        return this.service.notNullString()
    }

    // nullable, no bang-bang
    fun aString4(): String? {
        return this.serviceNotNullByDataFlow.nullableString()
    }

    // not nullable, no bang-bang
    fun aString5(): String? {
        return this.serviceNotNullByDataFlow.notNullString()
    }

    // nullable, safe-call
    fun aString6(): String? {
        val s = this.serviceNullable
        if (s != null) {
            return s.nullableString()
        } else {
            return null
        }
    }
}
