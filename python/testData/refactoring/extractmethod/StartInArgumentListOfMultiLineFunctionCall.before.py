def long_function_name(**kwargs): ...

def example_function():
    result = long_function_name(
        first_argument<caret>="value1",
        second_argument="value2",
        third_argument="value3"
    )
    processed = result.upper()
    return processed