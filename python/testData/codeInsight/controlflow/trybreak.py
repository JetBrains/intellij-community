def foo():
    try:
        for i in bar:
            break
    except:
        raise Exception()
    return 3