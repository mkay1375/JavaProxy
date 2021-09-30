package ir.mkay.javaproxy.proxyserver;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HttpResponseInfo extends HttpExchangeInfo {

    public static final HttpResponseInfo CONNECTION_ESTABLISHED;

    static {
        CONNECTION_ESTABLISHED = new HttpResponseInfo();
        CONNECTION_ESTABLISHED.setStatus(200);
        CONNECTION_ESTABLISHED.setProtocolVersion("HTTP/1.0");
        CONNECTION_ESTABLISHED.setReasonPhrase("Connection established");
        CONNECTION_ESTABLISHED.addHeader("proxy-agent", "JavaProxy");
    }

    private String protocolVersion;
    private int status;
    private String reasonPhrase;

    public void parseHttpExchangeFirstLine(String line) {
        var parts = line.split("\\s+", 3);
        this.protocolVersion = parts[0];
        this.status = Integer.parseInt(parts[1]);
        this.reasonPhrase = parts[2];
    }

    @Override
    public String getHttpExchangeFirstLine() {
        return this.protocolVersion + " " + this.status + " " + this.reasonPhrase;
    }

    public int getContentLength() {
        var contentLengthString = this.getHeaderFirstValue("content-length");
        if (contentLengthString != null) {
            return Integer.parseInt(contentLengthString);
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Response Info:\n");
        result.append(this.protocolVersion + " " + this.status + " " + this.reasonPhrase);
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
