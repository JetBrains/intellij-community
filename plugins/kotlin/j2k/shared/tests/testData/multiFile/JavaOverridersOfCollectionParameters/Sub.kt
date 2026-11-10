package test

class Sub : Base() {
    override fun handle(items: MutableList<String?>) {
        items.add("a")
    }
}
