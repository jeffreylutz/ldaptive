/* See LICENSE for licensing and NOTICE for copyright. */
package org.ldaptive.beans.spring;

import org.ldaptive.BindConnectionInitializer;
import org.ldaptive.ConnectionConfig;
import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.ad.handler.ObjectGuidHandler;
import org.ldaptive.ad.handler.ObjectSidHandler;
import org.ldaptive.auth.AuthenticationResponseHandler;
import org.ldaptive.auth.Authenticator;
import org.ldaptive.auth.FormatDnResolver;
import org.ldaptive.auth.SearchEntryResolver;
import org.ldaptive.auth.PooledBindAuthenticationHandler;
import org.ldaptive.auth.PooledSearchDnResolver;
import org.ldaptive.auth.ext.ActiveDirectoryAuthenticationResponseHandler;
import org.ldaptive.auth.ext.PasswordPolicyAuthenticationResponseHandler;
import org.ldaptive.control.PasswordPolicyControl;
import org.ldaptive.control.RequestControl;
import org.ldaptive.pool.BlockingConnectionPool;
import org.ldaptive.pool.IdlePruneStrategy;
import org.ldaptive.pool.PoolConfig;
import org.ldaptive.pool.PooledConnectionFactory;
import org.ldaptive.pool.SearchValidator;
import org.ldaptive.ssl.SslConfig;
import org.ldaptive.ssl.X509CredentialConfig;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * Spring namespace handler for ldaptive.
 *
 * @author  Middleware Services
 */
public class NamespaceHandler extends NamespaceHandlerSupport
{


  @Override
  public void init()
  {
    registerBeanDefinitionParser("anonymous-search-authenticator", new AnonSearchAuthenticatorBeanDefinitionParser());
    registerBeanDefinitionParser("bind-search-authenticator", new BindSearchAuthenticatorBeanDefinitionParser());
    registerBeanDefinitionParser("direct-authenticator", new DirectAuthenticatorBeanDefinitionParser());
    registerBeanDefinitionParser("ad-authenticator", new ADAuthenticatorBeanDefinitionParser());
  }


  /**
   * Parser for <pre>anonymous-search-authenticator</pre> elements.
   */
  private static class AnonSearchAuthenticatorBeanDefinitionParser
    extends AbstractSearchAuthenticatorBeanDefinitionParser
  {


    @Override
    protected String resolveId(
      final Element element,
      // CheckStyle:IllegalTypeCheck OFF
      final AbstractBeanDefinition definition,
      // CheckStyle:IllegalTypeCheck ON
      final ParserContext parserContext)
      throws BeanDefinitionStoreException
    {
      final String idAttrValue = element.getAttribute("id");
      return StringUtils.hasText(idAttrValue) ? idAttrValue : "anonymous-search-authenticator";
    }
  }


  /**
   * Parser for <pre>bind-search-authenticator</pre> elements.
   */
  private static class BindSearchAuthenticatorBeanDefinitionParser
    extends AbstractSearchAuthenticatorBeanDefinitionParser
  {


    @Override
    protected String resolveId(
      final Element element,
      // CheckStyle:IllegalTypeCheck OFF
      final AbstractBeanDefinition definition,
      // CheckStyle:IllegalTypeCheck ON
      final ParserContext parserContext)
      throws BeanDefinitionStoreException
    {
      final String idAttrValue = element.getAttribute("id");
      return StringUtils.hasText(idAttrValue) ? idAttrValue : "bind-search-authenticator";
    }
  }


  /**
   * Parser for <pre>direct-authenticator</pre> elements.
   */
  private static class DirectAuthenticatorBeanDefinitionParser extends AbstractAuthenticatorBeanDefinitionParser
  {


    @Override
    protected String resolveId(
      final Element element,
      // CheckStyle:IllegalTypeCheck OFF
      final AbstractBeanDefinition definition,
      // CheckStyle:IllegalTypeCheck ON
      final ParserContext parserContext)
      throws BeanDefinitionStoreException
    {
      final String idAttrValue = element.getAttribute("id");
      return StringUtils.hasText(idAttrValue) ? idAttrValue : "direct-authenticator";
    }


    @Override
    protected void doParse(
      final Element element, final ParserContext context, final BeanDefinitionBuilder builder)
    {
      builder.addConstructorArgValue(new FormatDnResolver(element.getAttribute("format")));
      builder.addConstructorArgValue(parseAuthHandler(element));
      if (element.hasAttribute("usePpolicy") && Boolean.valueOf(element.getAttribute("usePpolicy"))) {
        builder.addPropertyValue("authenticationResponseHandlers", new PasswordPolicyAuthenticationResponseHandler());
      }
    }
  }


  /**
   * Parser for <pre>ad-authenticator</pre> elements.
   */
  private static class ADAuthenticatorBeanDefinitionParser extends AbstractAuthenticatorBeanDefinitionParser
  {


    @Override
    protected String resolveId(
      final Element element,
      // CheckStyle:IllegalTypeCheck OFF
      final AbstractBeanDefinition definition,
      // CheckStyle:IllegalTypeCheck ON
      final ParserContext parserContext)
      throws BeanDefinitionStoreException
    {
      final String idAttrValue = element.getAttribute("id");
      return StringUtils.hasText(idAttrValue) ? idAttrValue : "ad-authenticator";
    }


    @Override
    protected void doParse(
      final Element element,
      final ParserContext context,
      final BeanDefinitionBuilder builder)
    {
      builder.addConstructorArgValue(new FormatDnResolver("%s@domain.com"));
      builder.addConstructorArgValue(parseAuthHandler(element));
      builder.addPropertyValue("authenticationResponseHandlers", new ActiveDirectoryAuthenticationResponseHandler());
      final SearchEntryResolver entryResolver = new SearchEntryResolver();
      entryResolver.setSearchEntryHandlers(new ObjectGuidHandler(), new ObjectSidHandler());
      builder.addPropertyValue("entryResolver", entryResolver);
    }
  }


  /**
   * Common implementation for search based authenticators.
   */
  private abstract static class AbstractSearchAuthenticatorBeanDefinitionParser
    extends AbstractAuthenticatorBeanDefinitionParser
  {


    @Override
    protected void doParse(
      final Element element,
      final ParserContext context,
      final BeanDefinitionBuilder builder)
    {
      final BeanDefinition pool = parseConnectionPool("search-pool", element);
      final BeanDefinitionBuilder connectionFactory = BeanDefinitionBuilder.genericBeanDefinition(
        PooledConnectionFactory.class);
      connectionFactory.addPropertyValue("connectionPool", pool);

      final BeanDefinitionBuilder resolver = BeanDefinitionBuilder.genericBeanDefinition(
        PooledSearchDnResolver.class);
      resolver.addPropertyValue("baseDn", element.getAttribute("baseDn"));
      resolver.addPropertyValue("subtreeSearch", element.getAttribute("subtreeSearch"));
      resolver.addPropertyValue("userFilter", element.getAttribute("userFilter"));
      resolver.addPropertyValue("connectionFactory", connectionFactory.getBeanDefinition());

      if (element.hasAttribute("usePpolicy")) {
        final BeanDefinitionBuilder responseHandler =  BeanDefinitionBuilder.rootBeanDefinition(
          AbstractAuthenticatorBeanDefinitionParser.class,
          "parsePpolicyAuthenticationResponseHandler");
        responseHandler.addConstructorArgValue(element.getAttribute("usePpolicy"));
        builder.addPropertyValue("authenticationResponseHandlers", responseHandler.getBeanDefinition());
      }
      builder.addConstructorArgValue(resolver.getBeanDefinition());
      builder.addConstructorArgValue(parseAuthHandler(element));
    }
  }


  /**
   * Common implementation for all authenticators.
   */
  private abstract static class AbstractAuthenticatorBeanDefinitionParser extends AbstractSingleBeanDefinitionParser
  {


    @Override
    protected Class<?> getBeanClass(final Element element)
    {
      return Authenticator.class;
    }


    /**
     * Creates a pooled authentication handler for performing binds.
     *
     * @param  element  containing configuration
     *
     * @return  pooled bind authentication handler bean definition
     */
    protected BeanDefinition parseAuthHandler(final Element element)
    {
      final BeanDefinitionBuilder authHandler = BeanDefinitionBuilder.genericBeanDefinition(
        PooledBindAuthenticationHandler.class);
      final BeanDefinitionBuilder connectionFactory = BeanDefinitionBuilder.genericBeanDefinition(
        PooledConnectionFactory.class);
      connectionFactory.addPropertyValue("connectionPool", parseConnectionPool("bind-pool", element));
      authHandler.addPropertyValue("connectionFactory", connectionFactory.getBeanDefinition());
      if (element.hasAttribute("usePpolicy")) {
        final BeanDefinitionBuilder control =  BeanDefinitionBuilder.rootBeanDefinition(
          AbstractAuthenticatorBeanDefinitionParser.class,
          "parsePpolicyControl");
        control.addConstructorArgValue(element.getAttribute("usePpolicy"));
        authHandler.addPropertyValue("authenticationControls", control.getBeanDefinition());
      }
      return authHandler.getBeanDefinition();
    }


    /**
     * Creates a blocking connection pool.
     *
     * @param  element  containing configuration
     *
     * @return  blocking connection pool bean definition
     */
    protected BeanDefinition parseConnectionPool(final String name, final Element element)
    {
      final BeanDefinitionBuilder pool = BeanDefinitionBuilder.genericBeanDefinition(BlockingConnectionPool.class);
      pool.addPropertyValue("name", name);
      final BeanDefinitionBuilder factory = BeanDefinitionBuilder.genericBeanDefinition(DefaultConnectionFactory.class);
      factory.addPropertyValue("connectionConfig", parseConnectionConfig(element));
      pool.addPropertyValue("connectionFactory", factory.getBeanDefinition());
      pool.addPropertyValue("poolConfig", parsePoolConfig(element));
      pool.addPropertyValue("blockWaitTime", element.getAttribute("blockWaitTime"));
      pool.addPropertyValue("failFastInitialize", element.getAttribute("failFastInitialize"));
      final BeanDefinitionBuilder pruneStrategy = BeanDefinitionBuilder.genericBeanDefinition(IdlePruneStrategy.class);
      pruneStrategy.addConstructorArgValue(element.getAttribute("prunePeriod"));
      pruneStrategy.addConstructorArgValue(element.getAttribute("idleTime"));
      pool.addPropertyValue("pruneStrategy", pruneStrategy.getBeanDefinition());
      pool.addPropertyValue("validator", new SearchValidator());
      pool.setInitMethodName("initialize");
      return pool.getBeanDefinition();
    }


    /**
     * Creates a connection config. If a bindDn and bindCredential are present, a {@link BindConnectionInitializer} is
     * inserted into the connection config.
     *
     * @param  element  containing configuration
     *
     * @return  connection config bean definition
     */
    protected BeanDefinition parseConnectionConfig(final Element element)
    {
      final BeanDefinitionBuilder connectionConfig = BeanDefinitionBuilder.genericBeanDefinition(
        ConnectionConfig.class);
      connectionConfig.addPropertyValue("ldapUrl", element.getAttribute("ldapUrl"));
      connectionConfig.addPropertyValue("useStartTLS", element.getAttribute("useStartTLS"));
      connectionConfig.addPropertyValue("useSSL", element.getAttribute("useSSL"));
      connectionConfig.addPropertyValue("connectTimeout", element.getAttribute("connectTimeout"));

      if (element.hasAttribute("trustCertificates")) {
        final BeanDefinitionBuilder credentialConfig = BeanDefinitionBuilder.genericBeanDefinition(
          X509CredentialConfig.class);
        credentialConfig.addPropertyValue("trustCertificates", element.getAttribute("trustCertificates"));
        final BeanDefinitionBuilder sslConfig = BeanDefinitionBuilder.genericBeanDefinition(SslConfig.class);
        sslConfig.addPropertyValue("credentialConfig", credentialConfig.getBeanDefinition());
        connectionConfig.addPropertyValue("sslConfig", sslConfig.getBeanDefinition());
      }

      if (element.hasAttribute("bindDn") && element.hasAttribute("bindCredential")) {
        final BeanDefinitionBuilder initializer = BeanDefinitionBuilder.genericBeanDefinition(
          BindConnectionInitializer.class);
        initializer.addPropertyValue("bindDn", element.getAttribute("bindDn"));
        initializer.addPropertyValue("bindCredential", element.getAttribute("bindCredential"));
        connectionConfig.addPropertyValue("connectionInitializer", initializer.getBeanDefinition());
      }
      return connectionConfig.getBeanDefinition();
    }


    /**
     * Creates a pool config.
     *
     * @param  element  containing configuration
     *
     * @return  pool config bean definition
     */
    protected BeanDefinition parsePoolConfig(final Element element)
    {
      final BeanDefinitionBuilder poolConfig = BeanDefinitionBuilder.genericBeanDefinition(PoolConfig.class);
      poolConfig.addPropertyValue("minPoolSize", element.getAttribute("minPoolSize"));
      poolConfig.addPropertyValue("maxPoolSize", element.getAttribute("maxPoolSize"));
      poolConfig.addPropertyValue("validateOnCheckOut", element.getAttribute("validateOnCheckOut"));
      poolConfig.addPropertyValue("validatePeriodically", element.getAttribute("validatePeriodically"));
      poolConfig.addPropertyValue("validatePeriod", element.getAttribute("validatePeriod"));
      return poolConfig.getBeanDefinition();
    }


    /**
     * Returns a {@link PasswordPolicyAuthenticationResponseHandler} if the supplied value is true.
     *
     * @param  value  of the usePpolicy attribute
     *
     * @return  {@link PasswordPolicyAuthenticationResponseHandler} or null
     */
    protected static AuthenticationResponseHandler parsePpolicyAuthenticationResponseHandler(final String value)
    {
      return Boolean.valueOf(value) ? new PasswordPolicyAuthenticationResponseHandler() : null;
    }


    /**
     * Returns a {@link PasswordPolicyControl} if the supplied value is true.
     *
     * @param  value  of the usePpolicy attribute
     *
     * @return  {@link PasswordPolicyControl} or null
     */
    protected static RequestControl parsePpolicyControl(final String value)
    {
      return Boolean.valueOf(value) ? new PasswordPolicyControl() : null;
    }
  }
}