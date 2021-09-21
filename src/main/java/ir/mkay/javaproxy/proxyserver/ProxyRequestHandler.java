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

    private static final Logger exchangeHeaderLog = LoggerFactory.getLogger(ProxyRequestHandler.class.getName() + "-EXCHANGE_HEADER");

    private static final byte[] CONNECTION_ESTABLISHED_RESPONSE = CONNECTION_ESTABLISHED.toHttpHeader().getBytes(StandardCharsets.ISO_8859_1);
    private static final int BUFFER_SIZE = 4 * 1024;
    private static final int MAX_HTTP_HEADER_SIZE = 16 * 1024;

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
            this.copyClientToProxyInAnotherThread();
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
                log.debug("{}ms - Cleaned Up", currentTimeMillis() - startTime);
            }
        }
    }

    private void initializeClient() throws IOException {
        this.clientSocket.setSoTimeout(clientTimeout);
        this.clientInput = new BufferedInputStream(this.clientSocket.getInputStream());
        this.clientOutput = this.clientSocket.getOutputStream();
    }

    private void readClientRequestInfo() throws IOException {
        var headerLines = this.readHttpHeader(this.clientInput).split(CRLF);
        for (int i = 0; i < headerLines.length; i++) {
            var line = headerLines[i];
            if (line == null || line.isBlank()) break;
            if (i == 0) this.clientRequestInfo.parseRequestFirstLine(line);
            else this.clientRequestInfo.parseHeader(line);
        }
        if (exchangeHeaderLog.isDebugEnabled()) {
            exchangeHeaderLog.debug("{}ms - Client {}", currentTimeMillis() - startTime, this.clientRequestInfo);
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

    private void copyClientToProxyInAnotherThread() {
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

        var headerLines = this.readHttpHeader(this.proxyInput).split(CRLF);
        for (int i = 0; i < headerLines.length; i++) {
            var line = headerLines[i];
            if (line == null || line.isBlank()) break;
            if (i == 0) this.proxyResponseInfo.parseResponseFirstLine(line);
            else this.proxyResponseInfo.parseHeader(line);
        }
        if (exchangeHeaderLog.isDebugEnabled()) {
            exchangeHeaderLog.debug("{}ms - Proxy {}", currentTimeMillis() - startTime, this.proxyResponseInfo);
        }
    }

    private void copyProxyToClient() throws IOException {
        this.copy(this.proxyInput, this.clientOutput);
    }

    private String readHttpHeader(BufferedInputStream input) throws IOException {
        var buffer = new byte[1024];
        int read, total = 0;
        int endOfHttpHeaderIndex = -1;
        StringBuilder header = new StringBuilder();
        input.mark(MAX_HTTP_HEADER_SIZE);
        while ((read = input.read(buffer)) > -1) {
            header.append(new String(buffer, 0, read));
            endOfHttpHeaderIndex = header.indexOf(END_OF_HTTP_HEADER, Math.max(total - 2, 0));
            total += read;
            if (endOfHttpHeaderIndex > 0) {
                break;
            }
            if (total > MAX_HTTP_HEADER_SIZE) {
                throw new IllegalStateException("HTTP header size exceeded");
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

    private void cleanUp() {
        IOUtils.tryToClose(this.proxyInput);
        IOUtils.tryToClose(this.proxyOutput);
        IOUtils.tryToClose(this.proxySocket);
        IOUtils.tryToClose(this.clientInput);
        IOUtils.tryToClose(this.clientOutput);
        IOUtils.tryToClose(this.clientSocket);
    }
}
