from __future__ import print_function


def cond1():
    return True


def cond2():
    return False


if __name__ == '__main__':
    xs = [1, 2, 3]
    if 2 in xs:
        print("YES")
    else:
        print("NO")

    if cond1() and cond2():
        print("YES")
    else:
        print("NO")
