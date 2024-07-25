class Main {
    fun f1(): ArrayList<String> {
        return ArrayList<String>()
    }

    fun f2(): ArrayList<String> {
        val list = f1()
        f3(list)
        return list
    }

    fun f3(list: ArrayList<String>) {
        for (item in list) {
            println(item.length)
        }
    }
}
