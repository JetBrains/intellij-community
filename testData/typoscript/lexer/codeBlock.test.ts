myObject {
field = title
   ifEmpty {  ignored text
       data = leveltitle:0  } bracket is ignored
     [i = 5]
    }    some ignored text
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