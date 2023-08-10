while true; <fold text='do...done' expand='true'>do
  mkdir $WEBDIR/"$DATE"
  while [ $HOUR -ne "00" ]; <fold text='do...done' expand='true'>do
    mkdir "$DESTDIR"
    sleep 3600
  done</fold>
  for i in $( ls ); <fold text='do...done' expand='true'>do
    echo item: $i
  done</fold>
done</fold>

for i in $( ls ); <fold text='do...done' expand='true'>do
done</fold>

for i in $( ls ); <fold text='do...done' expand='true'>do

done</fold>