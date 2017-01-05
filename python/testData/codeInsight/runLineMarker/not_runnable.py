def f():
    def g():
        pass

    if __name__ == '__main__':
        g()
    print(1)


f()
