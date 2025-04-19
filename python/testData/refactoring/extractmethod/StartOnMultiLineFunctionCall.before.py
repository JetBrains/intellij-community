def long_function_name(**kwargs): ...

def example_function():
    result = long_function_<caret>name(
        first_argument="value1",
        second_argument="value2",
        third_argument="value3"
    )
    processed = result.upper()
    return processed
