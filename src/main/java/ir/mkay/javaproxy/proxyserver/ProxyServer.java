package ir.mkay.javaproxy.proxyserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ProxyServer implements Closeable {

    private final ExecutorService executorService;
    private final ExecutorService requestHandlers;
    private final ExecutorService requestCopyClientToProxyHandlers;
    private final ServerSocket serverSocket;
    private final int clientTimeout;
    private final int proxyTimeout;
    private boolean started = false;


    public ProxyServer(@Value("${server.port}") int port,
                       @Value("${server.threads}") int threads,
                       @Value("${server.client-timeout}") int clientTimeout,
                       @Value("${server.proxy-timeout}") int proxyTimeout) throws IOException {
        this.executorService = Executors.newSingleThreadExecutor();
        this.requestHandlers = Executors.newFixedThreadPool(threads);
        this.requestCopyClientToProxyHandlers = Executors.newFixedThreadPool(threads);
        this.serverSocket = new ServerSocket(port);
        this.clientTimeout = clientTimeout;
        this.proxyTimeout = proxyTimeout;
    }

    @PostConstruct
    public void start() {
        if (!this.started) {
            this.executorService.submit(() -> {
               while (!Thread.interrupted()) {
                   this.accept();
               }
            });

            this.started = true;
            log.info("Proxy server started on port {}", this.serverSocket.getLocalPort());
        } else {
            log.warn("Proxy server is already started");
        }
    }

    @PreDestroy
    public void close() {
        if (this.started) {
            this.executorService.shutdown();
            this.requestHandlers.shutdown();
            this.requestCopyClientToProxyHandlers.shutdown();
            IOUtils.tryToClose(this.serverSocket);
        }
        log.info("Proxy server stopped");
    }

    private void accept() {
        try {
            var socket = this.serverSocket.accept();
            this.requestHandlers.submit(new ProxyRequestHandler(socket, this.clientTimeout, this.proxyTimeout, this.requestCopyClientToProxyHandlers));
        } catch (IOException e) {
            log.warn("Error accepting socket", e);
        }
    }
}
