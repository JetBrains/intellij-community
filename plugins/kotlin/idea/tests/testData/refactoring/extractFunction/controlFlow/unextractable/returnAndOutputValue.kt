fun testMe(value: String): String {
    <selection>if (value.isEmpty()) {
        return "empty"
    }
    if (value == "error") {
        return "Error"
    }
    val processed = "Success: $value"
    </selection>
    return processed
}