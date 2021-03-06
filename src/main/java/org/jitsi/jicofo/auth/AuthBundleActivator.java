/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jicofo.auth;

import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.Logger;

import org.eclipse.jetty.ajp.*;
import org.eclipse.jetty.server.*;
import org.jitsi.rest.*;
import org.jitsi.service.configuration.*;
import org.jitsi.util.*;
import org.osgi.framework.*;

import java.util.*;

/**
 * Implements <tt>BundleActivator</tt> for the OSGi bundle responsible for
 * authentication with external systems. Authentication URL pattern must be
 * configured in order to active the bundle {@link #LOGIN_URL_PNAME}.
 *
 * @author Pawel Domas
 */
public class AuthBundleActivator
    extends AbstractJettyBundleActivator
{
    /**
     * The prefix of the names of {@code ConfigurationService} and/or
     * {@code System} properties defined by {@code AuthBundleActivator}.
     */
    private static final String AUTH_PNAME = "org.jitsi.jicofo.auth";

    /**
     * The name of the {@code ConfigurationService} property which specifies the
     * pattern of authentication URL. See {@link ShibbolethAuthAuthority} for
     * more information.
     */
    public static final String LOGIN_URL_PNAME = AUTH_PNAME + ".URL";

    /**
     * The name of the {@code ConfigurationService} property which specifies the
     * pattern of logout URL. See {@link ShibbolethAuthAuthority} for more
     * information.
     */
    public static final String LOGOUT_URL_PNAME = AUTH_PNAME + ".LOGOUT_URL";

    /**
     * The name of the {@code ConfigurationService} property which disables auto
     * login feature. Authentication sessions are destroyed immediately when the
     * conference ends.
     */
    public static final String DISABLE_AUTOLOGIN_PNAME
        = AUTH_PNAME + ".DISABLE_AUTOLOGIN";

    /**
     * The {@code Logger} used by the {@code AuthBundleActivator} class and its
     * instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(AuthBundleActivator.class);

    /**
     * Reference to service registration of {@link AuthenticationAuthority}.
     */
    private
        ServiceRegistration<AuthenticationAuthority>
            authAuthorityServiceRegistration;

    /**
     * The instance of {@link AuthenticationAuthority}.
     */
    private AuthenticationAuthority authAuthority;

    static BundleContext bundleContext;

    /**
     * Initializes a new {@code AuthBundleActivator} instance.
     */
    public AuthBundleActivator()
    {
        super(AUTH_PNAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getDefaultPort()
    {
        // The idea of overriding Videobridge's default is probably an attempt
        // to have Videobridge and Jicofo running on the same machine with their
        // defaults and without them clashing.
        return 8888;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getDefaultTlsPort()
    {
        // The idea of overriding Videobridge's default is probably an attempt
        // to have Videobridge and Jicofo running on the same machine with their
        // defaults and without them clashing.
        return 8843;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Handler initializeHandlerList(
            BundleContext bundleContext,
            Server server)
        throws Exception
    {
        List<Handler> handlers = new ArrayList<Handler>();

        // Shibboleth
        if (authAuthority instanceof ShibbolethAuthAuthority)
        {
            ShibbolethAuthAuthority shibbolethAuthAuthority
                = (ShibbolethAuthAuthority) authAuthority;

            handlers.add(new ShibbolethHandler(shibbolethAuthAuthority));
        }

        // FIXME While Shibboleth is optional, the health checks of Jicofo (over
        // REST) are mandatory at the time of this writing. Make the latter
        // optional as well (in a way similar to Videobridge, for example).
        handlers.add(new org.jitsi.jicofo.rest.HandlerImpl(bundleContext));

        return initializeHandlerList(handlers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Server initializeServer(BundleContext bundleContext)
        throws Exception
    {
        Server server = super.initializeServer(bundleContext);

        if (authAuthority instanceof ShibbolethAuthAuthority)
        {
            // AJP
            Ajp13SocketConnector ajp13SocketConnector
                = new Ajp13SocketConnector();

            ajp13SocketConnector.setPort(8009);
            server.addConnector(ajp13SocketConnector);
        }

        return server;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        AuthBundleActivator.bundleContext = bundleContext;

        ConfigurationService cfg
            = ServiceUtils.getService(
                    bundleContext,
                    ConfigurationService.class);
        String loginUrl = cfg.getString(LOGIN_URL_PNAME);

        if (!StringUtils.isNullOrEmpty(loginUrl))
        {
            logger.info("Starting authentication service... URL: " + loginUrl);

            if (loginUrl.toUpperCase().startsWith("XMPP:"))
            {
                authAuthority
                    = new XMPPDomainAuthAuthority(loginUrl.substring(5));
            }
            else
            {
                String logoutUrl = cfg.getString(LOGOUT_URL_PNAME);

                authAuthority
                    = new ShibbolethAuthAuthority(loginUrl, logoutUrl);
            }
            logger.info("Auth authority: " + authAuthority);

            authAuthorityServiceRegistration
                = bundleContext.registerService(
                        AuthenticationAuthority.class,
                        authAuthority,
                        null);

            authAuthority.start();
        }

        super.start(bundleContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        if (authAuthorityServiceRegistration != null)
        {
            authAuthorityServiceRegistration.unregister();
            authAuthorityServiceRegistration = null;
        }
        if (authAuthority != null)
        {
            authAuthority.stop();
            authAuthority = null;
        }

        super.stop(bundleContext);

        AuthBundleActivator.bundleContext = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean willStart(BundleContext bundleContext)
        throws Exception
    {
        boolean b = super.willStart(bundleContext);

        if (b)
        {
            // Shibboleth works the same as every other web-based Single Sign-on
            // (SSO) system so it requires the Jetty HTTP server.
            b = (authAuthority instanceof ShibbolethAuthAuthority);

            // FIXME While Shibboleth is optional, the health checks of Jicofo
            // (over REST) are mandatory at the time of this writing. Make the
            // latter optional as well (in a way similar to Videobridge, for
            // example).
            if (!b)
                b = true;
        }
        return b;
    }
}
