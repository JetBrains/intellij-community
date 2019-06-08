def fn_with_except():
    try:
        raise Exception()
    except:
        pass


def test_except():
    fn_with_except()
    fn_with_except()


if __name__ == '__main__':
    test_except()
    print('TEST SUCEEDED')
