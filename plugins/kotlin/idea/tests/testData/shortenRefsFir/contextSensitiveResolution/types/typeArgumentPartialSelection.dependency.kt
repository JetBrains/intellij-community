package test

sealed class Result {
    class Success<T> : Result()
    class Failure : Result()
}

class Payload
