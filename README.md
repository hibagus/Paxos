# Paxos

To compile, use the following command.

~~~shell
javac -cp $PWD/junit-4.12.jar paxos/*.java
~~~

To run the test, use the following command.

~~~shell
for i in $(seq 10); do java -cp $PWD/junit-4.12.jar:$PWD/hamcrest-core-1.3.jar:. org.junit.runner.JUnitCore paxos.PaxosTest; done
~~~

# KVPaxos

To compile, use the following command.

~~~shell
javac -cp $PWD/junit-4.12.jar:. kvpaxos/*.java
~~~

To run the test, use the following command.

~~~shell
for i in $(seq 10); do java -cp $PWD/junit-4.12.jar:$PWD/hamcrest-core-1.3.jar:. org.junit.runner.JUnitCore kvpaxos.KVPaxosTest; done
~~~
