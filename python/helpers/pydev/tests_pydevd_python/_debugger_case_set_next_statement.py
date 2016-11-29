def method():
    a = 1
    print('call %s' % (a,))
    a = 2
    print('call %s' % (a,))
    a = 3

if __name__ == '__main__':
    method()
    print('TEST SUCEEDED!')
