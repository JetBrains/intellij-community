try:
    raise ExceptionGroup("asdf", [Exception("fdsa")])
except     *   Exception as ex:
    pass