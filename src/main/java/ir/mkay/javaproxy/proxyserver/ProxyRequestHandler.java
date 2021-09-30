package ir.mkay.javaproxy.proxyserver;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import static ir.mkay.javaproxy.proxyserver.HttpExchangeInfo.CRLF;
import static ir.mkay.javaproxy.proxyserver.HttpExchangeInfo.END_OF_HTTP_HEADER;
import static ir.mkay.javaproxy.proxyserver.HttpResponseInfo.CONNECTION_ESTABLISHED;
import static java.lang.System.currentTimeMillis;

@Slf4j
public class ProxyRequestHandler implements Runnable {

    private static final Logger exchangeInfoLog = LoggerFactory.getLogger(ProxyRequestHandler.class.getName() + "-EXCHANGE_INFO");

    private static final byte[] CONNECTION_ESTABLISHED_RESPONSE = CONNECTION_ESTABLISHED.toHttpString().getBytes(StandardCharsets.ISO_8859_1);
    private static final int BUFFER_SIZE = 4 * 1024;
    private static final int MAX_HTTP_STRING_SIZE = 16 * 1024;

    private final long startTime = currentTimeMillis();
    private final ExecutorService copyClientToProxyHandlers;

    private final int clientTimeout;
    private final HttpRequestInfo clientRequestInfo = new HttpRequestInfo();
    private final Socket clientSocket;
    private BufferedInputStream clientInput;
    private OutputStream clientOutput;

    private final int proxyTimeout;
    private final HttpResponseInfo proxyResponseInfo = new HttpResponseInfo();
    private Socket proxySocket;
    private BufferedInputStream proxyInput;
    private OutputStream proxyOutput;

    public ProxyRequestHandler(Socket socket, int clientTimeout, int proxyTimeout, ExecutorService copyClientToProxyHandlers) {
        this.clientSocket = socket;
        this.clientTimeout = clientTimeout;
        this.proxyTimeout = proxyTimeout;
        this.copyClientToProxyHandlers = copyClientToProxyHandlers;
    }

    @Override
    public void run() {
        this.handleRequest();
    }

    private void handleRequest() {
        try {
            this.initializeClient();
            this.readClientRequestInfo();
            this.connectToProxy();
            this.handleHttps();
            this.copyClientToProxyAsync();
            this.readProxyResponseInfo();
            this.copyProxyToClient();
        } catch (UnknownHostException e) {
            log.warn("Error on handling client request; UnknownHostException: {}", e.getMessage());
        } catch (SocketTimeoutException e) {
            log.warn("Error on handling client request; SocketTimeoutException: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Error on handling client request", e);
        } finally {
            this.cleanUp();
            if (log.isDebugEnabled()) {
                log.debug("{}ms - Cleaned Up", this.getElapsedTime());
            }
        }
    }

    private void initializeClient() throws IOException {
        this.clientSocket.setSoTimeout(clientTimeout);
        this.clientInput = new BufferedInputStream(this.clientSocket.getInputStream());
        this.clientOutput = this.clientSocket.getOutputStream();
    }

    private void readClientRequestInfo() throws IOException {
        this.readExchangeInfo(this.clientInput, this.clientRequestInfo);

        if (exchangeInfoLog.isDebugEnabled()) {
            exchangeInfoLog.debug("{}ms - Client {}", this.getElapsedTime(), this.clientRequestInfo);
        }
    }

    private void connectToProxy() throws IOException {
        var proxyAddress = InetAddress.getByName(this.clientRequestInfo.getTargetAsUrl().getHost());
        var proxyPort = this.clientRequestInfo.getTargetAsUrl().getPort();
        this.proxySocket = new Socket(proxyAddress, proxyPort > -1 ? proxyPort : 80);
        this.proxySocket.setSoTimeout(this.proxyTimeout);
        this.proxyInput = new BufferedInputStream(this.proxySocket.getInputStream());
        this.proxyOutput = this.proxySocket.getOutputStream();
    }

    private void handleHttps() throws IOException {
        if (this.clientRequestInfo.getMethod().equals("CONNECT")) {
            this.clientRequestInfo.setHttps(true);
            this.purge(this.clientInput);
            clientOutput.write(CONNECTION_ESTABLISHED_RESPONSE);
            clientOutput.flush();
        }
    }

    private void copyClientToProxyAsync() {
        this.copyClientToProxyHandlers.submit(() -> {
            try {
                this.copyClientToProxy();
            } catch (Exception e) {
                log.warn("Failed to copy client to proxy; {}: {}", e.getClass().getName(), e.getMessage());
            }
        });
    }

    private void copyClientToProxy() throws IOException {
        this.copy(this.clientInput, this.proxyOutput);
    }

    private void readProxyResponseInfo() throws IOException {
        if (this.clientRequestInfo.isHttps()) return;

        this.readExchangeInfo(this.proxyInput, this.proxyResponseInfo);

        if (exchangeInfoLog.isDebugEnabled()) {
            exchangeInfoLog.debug("{}ms - Proxy {}", this.getElapsedTime(), this.proxyResponseInfo);
        }
    }

    private void copyProxyToClient() throws IOException {
        this.copy(this.proxyInput, this.clientOutput);
    }

    private void readExchangeInfo(BufferedInputStream inputStream, HttpExchangeInfo httpExchangeInfo) throws IOException {
        var headerLines = this.readHttpString(inputStream).split(CRLF);
        for (int i = 0; i < headerLines.length; i++) {
            var line = headerLines[i];
            if (line == null || line.isBlank()) break;
            if (i == 0) httpExchangeInfo.parseHttpExchangeFirstLine(line);
            else httpExchangeInfo.parseHeader(line);
        }
    }

    private String readHttpString(BufferedInputStream input) throws IOException {
        var buffer = new byte[1024];
        int read, total = 0;
        int endOfHttpHeaderIndex = -1;
        StringBuilder header = new StringBuilder();
        input.mark(MAX_HTTP_STRING_SIZE);
        while ((read = input.read(buffer)) > -1) {
            header.append(new String(buffer, 0, read));
            endOfHttpHeaderIndex = header.indexOf(END_OF_HTTP_HEADER, Math.max(total - 2, 0));
            total += read;
            if (endOfHttpHeaderIndex > 0) {
                break;
            }
            if (total > MAX_HTTP_STRING_SIZE) {
                throw new IllegalStateException("HTTP string size exceeded");
            }
        }
        input.reset();
        if (endOfHttpHeaderIndex <= 0) return "";
        else return header.substring(0, endOfHttpHeaderIndex);
    }

    private void purge(InputStream inputStream) throws IOException {
        inputStream.readNBytes(inputStream.available());
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        var buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) > -1) {
            out.write(buffer, 0, read);
            if (in.available() <= 0) {
                out.flush();
            }
        }
    }

    private long getElapsedTime() {
        return currentTimeMillis() - this.startTime;
    }

    private void cleanUp() {
        IOUtils.tryToClose(this.proxyInput);
        IOUtils.tryToClose(this.proxyOutput);
        IOUtils.tryToClose(this.proxySocket);
        IOUtils.tryToClose(this.clientInput);
        IOUtils.tryToClose(this.clientOutput);
        IOUtils.tryToClose(this.clientSocket);
    }
}
