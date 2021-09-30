package ir.mkay.javaproxy.proxyserver;

import lombok.Getter;
import lombok.Setter;

import java.net.MalformedURLException;
import java.net.URL;

@Getter
@Setter
public class HttpRequestInfo extends HttpExchangeInfo {

    private String method;
    private String target;
    private String protocolVersion;
    private URL targetAsUrl;
    private boolean https;

    public void parseHttpExchangeFirstLine(String line) {
        var parts = line.split("\\s+");
        this.method = parts[0];
        this.target = parts[1];
        this.protocolVersion = parts[2];
        try {
            var target = this.target;
            if (!target.startsWith("http://")) {
                target = "http://" + target;
            }
            this.targetAsUrl = new URL(target);
        } catch (MalformedURLException ignore) {
        }
    }

    @Override
    public String getHttpExchangeFirstLine() {
        return  this.method + " " + this.target + " " + this.protocolVersion;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Request Info:\n");
        result.append(this.method + " " + this.target + " " + this.protocolVersion);
        result.append("\n");
        forEachHeader((name, value) -> {
            result.append(name);
            result.append(": ");
            result.append(value);
            result.append("\n");
        });
        return result.toString();
    }

}
