def foo():
    while True:
        try:
            break
        finally:
            pass
    print('b')