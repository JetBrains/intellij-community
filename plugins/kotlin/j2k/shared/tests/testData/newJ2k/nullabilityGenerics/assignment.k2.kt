internal class AssignmentLocal {
    var field: ArrayList<String> = ArrayList<String>()

    fun test(param: ArrayList<String>) {
        var local = field
        if (local.isEmpty()) {
            local = initLocal()
        } else {
            local = param
        }
    }

    private fun initLocal(): ArrayList<String> {
        return ArrayList<String>()
    }
}

internal class AssignmentLocalReverse {
    var field: ArrayList<String> = ArrayList<String>()

    fun test(param: ArrayList<String>) {
        var local = field
        if (local.isEmpty()) {
            local = initLocal()
        } else {
            local = param
        }
    }

    private fun initLocal(): ArrayList<String> {
        return ArrayList<String>()
    }
}

internal class AssignmentField {
    var field: ArrayList<String> = ArrayList<String>()

    fun test(param: ArrayList<String>) {
        if (field.isEmpty()) {
            field = initField()
        } else {
            field = param
        }
    }

    private fun initField(): ArrayList<String> {
        return ArrayList<String>()
    }
}

internal class AssignmentFieldReverse {
    var field: ArrayList<String> = ArrayList<String>()

    fun test(param: ArrayList<String>) {
        if (field.isEmpty()) {
            field = initField()
        } else {
            field = param
        }
    }

    private fun initField(): ArrayList<String> {
        return ArrayList<String>()
    }
}
