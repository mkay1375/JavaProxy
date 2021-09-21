package ir.mkay.javaproxy.proxyserver;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;

@Slf4j
public class IOUtils {

    private IOUtils() {
        // Static Utility Class
    }

    public static void tryToClose(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            log.debug("Closing {} failed", closeable.getClass().getName(), e);
        }
    }

}
