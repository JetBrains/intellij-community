while True:
    promt = "print command > "
    try:
        string = raw_input(promt)
    except :
        string = input(promt)
    print ("command was " + string)