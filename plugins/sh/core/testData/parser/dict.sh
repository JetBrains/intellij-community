$(( 1[1] ))
$(( [1] ))

A=(1 2 3)
A=(["a"]=1 ["aa"]=2 ["2"]=3)
A=(1**1 ["aa"]=2 ["2"]=3)

A=([1]=2)


A=(["a"]=1)

A=(["a"]=1 ["aa"]=2 ["2"]=3)

distrs=(
    jdk-6.45-linux_i586.bin     \
    jdk-6.45-linux_x64.bin      \
    jdk-5.0.22-linux_x64.bin    \
    jdk-5.0.22-linux_i586.bin   \
    jdk-1.4.2.19-linux_i586.bin \
)
for FILE in ${distrs[*]}
do
    wget -nv https://repo.labs.intellij.net/download/oracle/$FILE -O $FILE
done

# A=([1]=2 =2*${var}) // fixme
