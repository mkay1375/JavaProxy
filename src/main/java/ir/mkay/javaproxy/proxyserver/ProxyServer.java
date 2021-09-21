package ir.mkay.javaproxy.proxyserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ProxyServer implements ApplicationRunner {

    private final ExecutorService executorService;
    private final ExecutorService requestHandlers;
    private final ExecutorService requestCopyClientToProxyHandlers;
    private final ServerSocket serverSocket;
    private final int clientTimeout;
    private final int proxyTimeout;
    private final CodingMode codingMode;
    private final byte codingConstant;
    private final String staticProxyServerAddress;
    private final int staticProxyServerPort;

    private boolean started = false;


    public ProxyServer(@Value("${server.port}") int port,
                       @Value("${server.threads}") int threads,
                       @Value("${server.client-timeout}") int clientTimeout,
                       @Value("${server.proxy-timeout}") int proxyTimeout,
                       @Value("${server.coding-mode}") CodingMode codingMode,
                       @Value("${server.coding-constant}") byte codingConstant,
                       @Value("${server.static-proxy-server.address}") String staticProxyServerAddress,
                       @Value("${server.static-proxy-server.port}") int staticProxyServerPort) throws IOException {
        this.executorService = Executors.newSingleThreadExecutor();
        this.requestHandlers = Executors.newFixedThreadPool(threads);
        this.requestCopyClientToProxyHandlers = Executors.newFixedThreadPool(threads);
        this.serverSocket = new ServerSocket(port);
        this.clientTimeout = clientTimeout;
        this.proxyTimeout = proxyTimeout;
        this.codingMode = codingMode;
        this.codingConstant = codingConstant;
        this.staticProxyServerAddress = staticProxyServerAddress;
        this.staticProxyServerPort = staticProxyServerPort;
    }

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

    @Override
    public void run(ApplicationArguments args) {
        this.start();
    }

    @PreDestroy
    public void stop() {
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
            var handler = new ProxyRequestHandler(socket,
                    this.clientTimeout,
                    this.proxyTimeout,
                    this.requestCopyClientToProxyHandlers,
                    this.codingMode,
                    this.codingConstant,
                    this.staticProxyServerAddress,
                    this.staticProxyServerPort);
            this.requestHandlers.submit(handler);
        } catch (IOException e) {
            log.warn("Error accepting socket", e);
        }
    }
}
