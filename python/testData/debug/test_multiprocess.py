from concurrent.futures import ProcessPoolExecutor
def my_foo(arg_):
    return arg_

def main():
    arg = ['Result:OK']
    with ProcessPoolExecutor(1) as exec:
        result = exec.map(my_foo, arg)
        for i in result:
            print(i)

if __name__ == '__main__':
    main()