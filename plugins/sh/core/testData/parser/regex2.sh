[[ (a =~ (b)) && (b =~ ((c))) ]]

[[ "hello world" =~ hello\ world ]]          && echo match 1
[[ "hello world" =~ hello[[:space:]]world ]] && echo match 2
[[ "hello world" =~ "hello world" ]]         && echo match 3
[[ 'hello world' =~ 'hello world' ]]         && echo match 4
[[ "hello world" =~ $'hello world' ]]        && echo match 5

[[ "hello world"=~hello\ world ]]          && echo match 6
[[ "hello world"=~hello[[:space:]]world ]] && echo match 7
[[ "hello world"=~"hello world" ]]         && echo match 8

pattern="hello world"
[[ "hello world" =~ $pattern ]]           && echo match 9
[[ "hello world" =~ "$pattern" ]]         && echo match 10
[[ "hello world" =~ "${pattern}" ]]       && echo match 11
[[ "hello world" =~ `echo $pattern` ]]    && echo match 12
[[ "hello world" =~ $(echo $pattern) ]]   && echo match 13
[[ "hello world" =~ "$(echo $pattern)" ]] && echo match 14

pattern='hello[[:space:]]world'
[[ "hello world" =~ $pattern ]]   && echo match 15
[[ "hello world" =~ "$pattern" ]] || echo no match 16

[[ "hello world" =~ hello[[:space:]]world ]]   && echo match 17
[[ "hello world" =~ "hello[[:space:]]world" ]] || echo no match 18

pattern='hello world 42'
[[ "hello world 42" =~ "hello world $((7*6))" ]]   && echo match 19

[[ "hello world 42" =~ [a-z]+[[:space:]][a-z]+[[:space:]][0-9][0-9] ]]   && echo match 20

[[ ("hello world" =~ hello\ world) || (hello =~ noMatch ) ]] && echo match 21
[[ ("hello world" =~ hello\ world) && (hello =~ noMatch ) ]] || echo no match 22

[[   ("hello world" =~ "hello world")
  && ( ('hello world' =~ $(echo hello world) ) || 'hello world' =~ 'no match') ]] && echo match 23

[[ ( ( ("hello world" =~ "hello world") || 'a' =~ ([a-z]) ) && ( ('hello world' =~ $(echo hello world)) )
  || 'hello world' =~ 'no match') ]] && echo match 24

[[ ("hello world" =~ ("hello my world"|'hello worlds'|'world')) ]] && echo match 25

[[ ("hello world" =~ ("hello my world" | 'hello worlds' | 'world')) ]] && echo match 26