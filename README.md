# HTTP failover sample (with camel, spring-boot) 
The 'peers' property is to define N endpoints to failover the request (with the given order)

### Build
You may need to compile it first:

	mvn install

### Run
To run the example type

	mvn spring-boot:run

You can also execute the jar directly:

	java -jar target/failover.war


### Remote Shell

The example ships with remote shell enabled which includes the Camel commands as well, so you can SSH into the running Camel application and use the camel commands to list / stop routes etc.

You can SSH into the JVM using

    ssh -p 2000 user@localhost

And then use `pass` as password.
