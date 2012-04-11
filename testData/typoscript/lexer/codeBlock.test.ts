myObject {
field = title
   ifEmpty {  IGNORED_TEXT
       data = leveltitle:0  } bracket is ignored
     [i = 5]
    }    some IGNORED_TEXT
}
} this brace is also ignored by parser

second.object{
{ ignored opening bracket
 .10 = left
 prop < test
 text(
  } ignored bracket
 )
}

third.object{
  do.not.ignore{
    i=5
  }
}