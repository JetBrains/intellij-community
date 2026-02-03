def f(value, value1, value2):
    if value in (
            value1, value2) or value == 0:  # <- missing continuation indent here
        return False
