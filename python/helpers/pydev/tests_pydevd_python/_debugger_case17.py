def get_here():
    a = 10

def foo(func): 
    return func

def m1(): # @DontTrace
    get_here()

# @DontTrace
def m2():
    get_here()

# @DontTrace
@foo
def m3():
    get_here()

@foo
@foo
def m4(): # @DontTrace
    get_here()


def main():

    m1()
    
    m2()
    
    m3()
    
    m4()

if __name__ == '__main__':
    main()
    
    print('TEST SUCEEDED')
