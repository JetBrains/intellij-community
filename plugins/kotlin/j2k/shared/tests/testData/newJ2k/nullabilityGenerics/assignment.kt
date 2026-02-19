internal class AssignmentLocal {
    var field: ArrayList<String> = ArrayList()

    fun test(param: ArrayList<String>) {
        var local = field
        local = if (local.isEmpty()) {
            initLocal()
        } else {
            param
        }
    }

    private fun initLocal(): ArrayList<String> {
        return ArrayList()
    }
}

internal class AssignmentLocalReverse {
    var field: ArrayList<String> = ArrayList()

    fun test(param: ArrayList<String>) {
        var local = field
        local = if (local.isEmpty()) {
            initLocal()
        } else {
            param
        }
    }

    private fun initLocal(): ArrayList<String> {
        return ArrayList()
    }
}

internal class AssignmentField {
    var field: ArrayList<String> = ArrayList()

    fun test(param: ArrayList<String>) {
        field = if (field.isEmpty()) {
            initField()
        } else {
            param
        }
    }

    private fun initField(): ArrayList<String> {
        return ArrayList()
    }
}

internal class AssignmentFieldReverse {
    var field: ArrayList<String> = ArrayList()

    fun test(param: ArrayList<String>) {
        field = if (field.isEmpty()) {
            initField()
        } else {
            param
        }
    }

    private fun initField(): ArrayList<String> {
        return ArrayList()
    }
}
