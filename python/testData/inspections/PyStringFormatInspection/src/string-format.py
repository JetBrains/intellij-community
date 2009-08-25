'#%(language)s has %(#)03d quote types.' % {'language': "Python", "#": 2}  #ok
'%d %s' % 5  #Too few arguments for format string
'Hello world' % 25 #Too many arguments for format string
"%(name)f(name)" % {'name': 23.2} #ok
"%()s" % {'': "name"} #ok
'test%(name)' % {'name': 23} #There are no format specifier character
'work%*d' % (2, 34) #ok
'work%(name)*d' % (12, 32) #Can't use '*' in formats when using a mapping
'%*.*d' % (2, 5, 5) #ok
'%*.*d' % (2, 4) #Too few arguments for format string
'%*.*d' % (2, 4, 5, 6) #Too many arguments for format string
'%**d' % (2, 5) #There are no format specifier character
'%(name1)s %(name2)s (name3) %s' % {'name1': 'a', 'name2': 'b', 'name3': 'c'} #Too few mapping keys
'%(name1s' % {'name1': 'a'} #Too few mapping keys
'%%%(name)ld' % {'name': 12} #ok
"%(name)f(name)" % 23.2 #Format requires a mapping
"%(name)f(name)" % (23.2) #Format requires a mapping
'%d%d' % {'name1': 2, 'name2': 3} #Format doesn't require a mapping
'%12.2f' % 2.74 #ok
'Hello world' % () #ok
'Hello world' % [] #ok
'Hello world' % {} #ok
'%d%d' % ((5), (5)) #ok
"%(name)d %(name)d" % {"name": 43} #ok
"%(name)d" % {'a': 4, "name": 5} #ok
'%% name %(name)c' % {'a': 4} #One of keys has no following argument
'%d %u %f %F %s %r' % (2, 3, 4.1, 4.0, "name", "str") #ok
'%d %d %d' % (4, "a", "b") #Unexpected type
'%f %f %f' % (4, 5, "test") #Unexpected type
'%d' % "name" #Unexpected type
m = {'language': "Python", "#": 2}
'#%(language)s has %(#)03d quote types.' % m  #ok
i = "test"
'%(name)s' % {'name': i}  #ok
'%s' % i  #ok
'%f' % i  #Unexpected type
'%f' % 2  #ok
s = "%s" % "a".upper() #ok
x = ['a', 'b', 'c']
print "%d: %s" % (len(x), ", ".join(x)) #ok
m = [1, 2, 3, 4, 5]
"%d" % m[0]  #ok
"%d %s" % (m[0], m[4])  #ok
"%s" % m  #ok
"%s" % m[1:3]  #ok
"%d" % m[1:2]  #Unexpected type
"%d" % m  #Unexpected type
"%d" % []  #Too few arguments for format string
#"%s" % []  #ok
#t = (1, 2, 3, 4, 5)
#"%d and %d" % t[1:3]  #ok