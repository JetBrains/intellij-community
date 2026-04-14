from sub_project_a import hello as hello_a
from sub_project_b import hello as hello_b

def main():
    print(hello_a())
    print(hello_b())

if __name__ == "__main__":
    main()
