package redis.clients.jedis;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import redis.clients.jedis.exceptions.InvalidURIException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.util.JedisURIHelper;

/**
 * PoolableObjectFactory custom impl.
 */
public class JedisFactory implements PooledObjectFactory<Jedis> {
  protected final AtomicReference<HostAndPort> hostAndPort = new AtomicReference<HostAndPort>();
  protected final int connectionTimeout;
  protected final int soTimeout;
  protected final String password;
  protected final int database;
  protected final String clientName;
  protected final boolean ssl;
  protected final SSLSocketFactory sslSocketFactory;
  protected final SSLParameters sslParameters;
  protected final HostnameVerifier hostnameVerifier;

  public JedisFactory(final String host, final int port, final int connectionTimeout,
      final int soTimeout, final String password, final int database, final String clientName,
      final boolean ssl, final SSLSocketFactory sslSocketFactory, final SSLParameters sslParameters,
      final HostnameVerifier hostnameVerifier) {
    this.hostAndPort.set(new HostAndPort(host, port));
    this.connectionTimeout = connectionTimeout;
    this.soTimeout = soTimeout;
    this.password = password;
    this.database = database;
    this.clientName = clientName;
    this.ssl = ssl;
    this.sslSocketFactory = sslSocketFactory;
    this.sslParameters = sslParameters;
    this.hostnameVerifier = hostnameVerifier;
  }

  public JedisFactory(final URI uri, final int connectionTimeout, final int soTimeout,
      final String clientName, final boolean ssl, final SSLSocketFactory sslSocketFactory,
      final SSLParameters sslParameters, final HostnameVerifier hostnameVerifier) {
    if (!JedisURIHelper.isValid(uri)) {
      throw new InvalidURIException(String.format(
        "Cannot open Redis connection due invalid URI. %s", uri.toString()));
    }

    this.hostAndPort.set(new HostAndPort(uri.getHost(), uri.getPort()));
    this.connectionTimeout = connectionTimeout;
    this.soTimeout = soTimeout;
    this.password = JedisURIHelper.getPassword(uri);
    this.database = JedisURIHelper.getDBIndex(uri);
    this.clientName = clientName;
    this.ssl = ssl;
    this.sslSocketFactory = sslSocketFactory;
    this.sslParameters = sslParameters;
    this.hostnameVerifier = hostnameVerifier;
  }

  public void setHostAndPort(final HostAndPort hostAndPort) {
    this.hostAndPort.set(hostAndPort);
  }

  @Override
  public void activateObject(PooledObject<Jedis> pooledJedis) throws Exception {
    final BinaryJedis jedis = pooledJedis.getObject();
    if (jedis.getDB() != database) {
      jedis.select(database);
    }

  }

  @Override
  public void destroyObject(PooledObject<Jedis> pooledJedis) throws Exception {
    final BinaryJedis jedis = pooledJedis.getObject();
    if (jedis.isConnected()) {
      try {
        try {
          jedis.quit();
        } catch (Exception e) {
        }
        jedis.disconnect();
      } catch (Exception e) {

      }
    }

  }

  @Override
  public PooledObject<Jedis> makeObject() throws Exception {
    final Jedis jedis = makeJedis();

    try {
      jedis.connect();
      if (null != this.password) {
        jedis.auth(this.password);
      }
      if (database != 0) {
        jedis.select(database);
      }
      if (clientName != null) {
        jedis.clientSetname(clientName);
      }
    } catch (JedisException je) {
      jedis.close();
      throw je;
    }

    return new DefaultPooledObject<Jedis>(jedis);

  }

  protected Jedis makeJedis() {
    final HostAndPort hostAndPort = this.hostAndPort.get();
    return new Jedis(hostAndPort.getHost(), hostAndPort.getPort(), connectionTimeout,
            soTimeout, ssl, sslSocketFactory, sslParameters, hostnameVerifier);
  }

  @Override
  public void passivateObject(PooledObject<Jedis> pooledJedis) throws Exception {
    // TODO maybe should select db 0? Not sure right now.
  }

  @Override
  public boolean validateObject(PooledObject<Jedis> pooledJedis) {
    final BinaryJedis jedis = pooledJedis.getObject();
    try {
      HostAndPort hostAndPort = this.hostAndPort.get();

      String connectionHost = jedis.getClient().getHost();
      int connectionPort = jedis.getClient().getPort();

      return hostAndPort.getHost().equals(connectionHost)
          && hostAndPort.getPort() == connectionPort && jedis.isConnected()
          && jedis.ping().equals("PONG");
    } catch (final Exception e) {
      return false;
    }
  }
}