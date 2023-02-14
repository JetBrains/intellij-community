try:
    raise ExceptionGroup("asdf", [Exception("fdsa")])
except* Exception as ex:
    print(ex)
    for e in ex.exceptions:
        print(e)