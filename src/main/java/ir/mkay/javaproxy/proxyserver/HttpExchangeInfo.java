package ir.mkay.javaproxy.proxyserver;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Getter
public abstract class HttpExchangeInfo {

    public static final String CRLF = "\r\n";
    public static final String END_OF_HTTP_HEADER = CRLF + CRLF;

    protected Map<String, List<String>> headers = new HashMap<>();

    public void parseHeader(String header) {
        var parts = header.split("\\s*:\\s*");
        this.addHeader(parts[0], parts[1]);
    }

    public void addHeader(String name, String value) {
        this.headers.computeIfAbsent(name.toLowerCase(), n -> new ArrayList<>()).add(value);
    }

    public List<String> getHeaderValues(String name) {
        return this.headers.get(name.toLowerCase());
    }

    public String getHeaderFirstValue(String name) {
        var values = this.headers.get(name.toLowerCase());
        return values != null && values.size() > 0 ? values.get(0) : null;
    }

    public void forEachHeader(BiConsumer<String, String> headerConsumer) {
        this.headers.forEach((name, values) -> {
            values.forEach(value -> {
                headerConsumer.accept(name, value);
            });
        });
    }

    public String toHttpHeader() {
        StringBuilder result = new StringBuilder();
        result.append(this.getHttpHeaderFirstLine());
        result.append(CRLF);
        this.forEachHeader((name, value) -> {
            result.append(name);
            result.append(": ");
            result.append(value);
            result.append(CRLF);
        });
        result.append(CRLF);
        return result.toString();
    }

    public abstract String getHttpHeaderFirstLine();
}
