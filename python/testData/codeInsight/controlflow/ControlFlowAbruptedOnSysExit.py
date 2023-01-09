try:
    n = int(sys.argv[1])
except ValueError:
    print("both arguments should be numbers")
    sys.exit()

print("Please, input " + str(n) + " file names")