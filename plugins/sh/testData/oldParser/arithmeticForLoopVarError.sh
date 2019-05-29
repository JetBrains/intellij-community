for (( a *2 < 4; 3 >= a; a = a*2 )) ; do hey $a; done;
#$a is here written as DOLLAR, WORD which is an invalid combination
#this has to be detected as an error
