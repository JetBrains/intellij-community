data class Product(val id: String, val productName: String)

fun test(productName: String, product: Product) {
    val (id, n/*rename*/ame) = product
    println(productName)
}