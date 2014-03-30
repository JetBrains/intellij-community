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
'%% name %(name)c' % {'a': 4} #Key 'name' has no following argument
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
'%f' % (2 * 3 + 5)  #ok
s = "%s" % "a".upper() #ok
x = ['a', 'b', 'c']
print "%d: %s" % (len(x), ", ".join(x)) #ok
m = [1, 2, 3, 4, 5]
"%d" % m[0]  #ok
"%d %s" % (m[0], m[4])  #ok
"%s" % m  #ok
"%s" % m[1:3]  #ok
"%d" % m[1:2]  #ok
"%d" % m  #Unexpected type
"%d" % []  #Unexpected type
def greet(all):
    print "Hello %s" % ("World" if all else "Human") #ok
"%s" % [x + 1 for x in [1, 2, 3, 4]]  #ok
"%s" % [x + y for x in []]  #ok
"%s" % []  #ok
"%f" % [x + 1 for x in [1, 2, 3, 4]]  #Unexpected type
"%d %d" % (3, 5)  #ok
"Hello %s %s" % tuple(['world', '!'])  #ok

def foo(a):
  if a == 1:
    return "a", "b"
  else:
    return "c", "d"
print "%s" % foo(1)  #Too many arguments for format string

print("| [%(issue_id)s|http://youtrack.jetbrains.net/issue/%(issue_id)s] (%(issue_type)s)|%(summary)s|" % (issue_id, issue_type, summary)) #Format requires a mapping (PY-704)

my_list = list()
for i in range(0,3):
    my_list.append( ("hey", "you") )

for item in my_list:
    print '%s %s' % item   # ok (PY-734)

def bar():
    return None
"%s %s" % bar()   #Too few arguments for format string

"%s" % {} # ok; str() works
"%s" % {'a': 1, 'b': 2} # ok, no names in template and arg counts don't match
"%s" % object() # ok, str() works
"foo" % {'bar':1, 'baz':2} # ok: empty template that could use names

a = ('a', 1) if 1 else ('b', 2)
"%s is %d" % a # ok, must infer unified tuple type
#PY-3064, because original type of a is tuple, not list
a = (1,2,3)
print '%d:%d' % a[:2]
print '%d:%d' % a[1:2]

string = "qwerty"
print '%d:%d' % string[:2]
print '%s:%s' % string[:2]
print '%s' % string[:2]
print '%d' % string[:2]

my_tuple = (1,2,3,4,5,6,7,8)
print '%d, %d' % my_tuple[:7:3]
print '%d, %d, %d' % my_tuple[:7:3]
print '%d, %d, %d, %d' % my_tuple[:7:3]