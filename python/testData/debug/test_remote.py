import sys

if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.stderr.write("Not enough arguments")
        sys.exit(1)

    port = int(sys.argv[1])

    x = 0

    import pydevd
    pydevd.settrace('localhost', port=port, stdoutToServer=True, stderrToServer=True)

    x = 1
    x = 2
    x = 3

    print("OK")
