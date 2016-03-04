def m1():
    print('m1')

def m2(): # @DontTrace
    m1()
    print('m2')

def m3():
    m2()
    print('m3')

if __name__ == '__main__':
    m3()

    print('TEST SUCEEDED')
