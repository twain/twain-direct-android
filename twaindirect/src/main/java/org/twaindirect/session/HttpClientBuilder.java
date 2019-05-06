package org.twaindirect.session;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.InMemoryDnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

/**
 * Helper class that constructs an HttpClient configured for TWAIN Direct usage.
 *
 * There are two specific things we need to when creating the request:
 *
 * We need to supply our own DNS resolver, with a mapping from the IP address to the
 * mDNS name for the scanner host, because Android doesn't support .local name resolution.
 *
 * And we need to configure the https stack to not require chaning back to one of the big
 * SSL cert vendors because the scanner self-signs a cert for its local name.
 *
 * Used by HttpBlockRequest and HttpJsonRequest.
 */
public class HttpClientBuilder {
    private static final Logger logger = Logger.getLogger(HttpClientBuilder.class.getName());

    public static CloseableHttpClient createHttpClient(String host, String ipaddr) throws UnknownHostException {

        try {
            InMemoryDnsResolver resolver = null;
            if (ipaddr != null) {
                resolver = new InMemoryDnsResolver();
                resolver.add(host, InetAddress.getByName(ipaddr));
            }

            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                    builder.build());

            BasicHttpClientConnectionManager connManager = new BasicHttpClientConnectionManager(
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("http", PlainConnectionSocketFactory.getSocketFactory())
                            .register("https", sslsf)
                            .build(),
                    null, /* Default ConnectionFactory */
                    null, /* Default SchemePortResolver */
                    resolver /* Our DnsResolver */
            );

            CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClientBuilder.create().setConnectionManager(connManager).setSSLSocketFactory(sslsf).build();
            return httpClient;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        } catch (KeyStoreException e) {
            e.printStackTrace();
            return null;
        } catch (KeyManagementException e) {
            e.printStackTrace();
            return null;
        }
    }
}
