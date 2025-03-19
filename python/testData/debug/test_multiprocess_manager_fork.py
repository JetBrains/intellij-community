from multiprocessing import Manager, set_start_method

if __name__ == '__main__':
    set_start_method('fork')
    manager = Manager()
    print("Hello, World")  # breakpoint
    print("Done!")
