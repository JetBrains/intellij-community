try:
    raise ExceptionGroup("asdf", [Exception("fdsa")])
except<error descr="Python version 3.10 does not support except* part">*</error> Exception as ex:
    print(ex)
    for e in ex.exceptions:
        print(e)