# JavaProxy

An HTTP/HTTPS proxy server implemented in Java using `java.net.SocketServer`;
I've learned a lot and used ideas from [this project](https://github.com/stefano-lupo/Java-Proxy-Server).

I haven't put much time in this project and there are rooms for improvement, but it works.

I've used Spring Boot for its logging and externalized configuration features;
not the most efficient choice but one of (if not THE) most easy one :)

## Usage

### Cloning Source Code

* Install Java 17 and Maven
* `git clone https://github.com/mkay1375/JavaProxy`
* `cd JavaProxy`
* `mvn spring-boot:run`

In order to change port (default port is `8765`) modify `application.properties` file.

### Running Jar File

* Install Java 17
* Download `jar` file from [releases](https://github.com/mkay1375/JavaProxy/releases)
* Run it using `java -jar JavaProxy-VERSION.jar`
* Or run it on custom port using `java -jar JavaProxy-VERSION.jar --server.port=CUSTOM_PORT`
