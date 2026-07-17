package pkg

sealed class MyResult {
    class Ok(val value: String) : MyResult()
    class Err(val message: String) : MyResult()
}
