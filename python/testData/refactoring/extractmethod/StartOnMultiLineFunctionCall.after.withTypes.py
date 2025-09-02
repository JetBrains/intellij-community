def long_function_name(**kwargs): ...

def example_function():
    result = extracted()
    processed = result.upper()
    return processed


def extracted() -> Any:
    return long_function_name(
        first_argument="value1",
        second_argument="value2",
        third_argument="value3"
    )