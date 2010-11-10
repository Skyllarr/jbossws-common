/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.wsf.common.invocation;

import java.security.Principal;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.jboss.wsf.common.injection.InjectionHelper;
import org.jboss.wsf.common.injection.PreDestroyHolder;
import org.jboss.wsf.spi.SPIProvider;
import org.jboss.wsf.spi.SPIProviderResolver;
import org.jboss.wsf.spi.deployment.Endpoint;
import org.jboss.wsf.spi.invocation.Invocation;
import org.jboss.wsf.spi.invocation.InvocationContext;
import org.jboss.wsf.spi.invocation.ResourceInjector;
import org.jboss.wsf.spi.invocation.ResourceInjectorFactory;
import org.jboss.wsf.spi.metadata.injection.InjectionsMetaData;
import org.w3c.dom.Element;

/**
 * Handles invocations on JAXWS endpoints.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:tdiesler@redhat.com">Thomas Diesler</a>
 */
public final class InvocationHandlerJAXWS extends AbstractInvocationHandlerJSE
{

   private static final String POJO_JNDI_PREFIX = "java:comp/env/";

   /**
    * Constructor.
    */
   public InvocationHandlerJAXWS()
   {
      super();
   }

   /**
    * Injects resources on target bean and calls post construct method.
    * Finally it registers target bean for predestroy phase.
    *
    * @param endpoint used for predestroy phase registration process
    * @param invocation current invocation
    */
   @Override
   protected void onEndpointInstantiated(final Endpoint endpoint, final Invocation invocation)
   {
      final InjectionsMetaData injectionsMD = endpoint.getAttachment(InjectionsMetaData.class);
      final Object targetBean = this.getTargetBean(invocation);

      this.log.debug("Injecting resources on JAXWS JSE endpoint: " + targetBean);
      if (injectionsMD != null)
         InjectionHelper.injectResources(targetBean, injectionsMD, endpoint.getJNDIContext());

      final SPIProvider spiProvider = SPIProviderResolver.getInstance().getProvider();
      final ResourceInjectorFactory resourceInjectorFactory = spiProvider.getSPI(ResourceInjectorFactory.class);
      final ResourceInjector wsContextInjector = resourceInjectorFactory.newResourceInjector();
      wsContextInjector.inject(targetBean, ThreadLocalAwareWebServiceContext.getInstance());

      this.log.debug("Calling postConstruct method on JAXWS JSE endpoint: " + targetBean);
      InjectionHelper.callPostConstructMethod(targetBean);

      endpoint.addAttachment(PreDestroyHolder.class, new PreDestroyHolder(targetBean));
   }

   /**
    * Injects webservice context on target bean.
    *
    *  @param invocation current invocation
    */
   @Override
   protected void onBeforeInvocation(final Invocation invocation)
   {
      final WebServiceContext wsContext = this.getWebServiceContext(invocation);
      ThreadLocalAwareWebServiceContext.getInstance().setMessageContext(wsContext);
   }

   /**
    * Cleanups injected webservice context on target bean.
    *
    * @param invocation current invocation
    */
   @Override
   protected void onAfterInvocation(final Invocation invocation)
   {
      ThreadLocalAwareWebServiceContext.getInstance().setMessageContext(null);
   }

   public Context getJNDIContext(final Endpoint ep) throws NamingException
   {
      return (Context)new InitialContext().lookup(POJO_JNDI_PREFIX);
   }

   /**
    * Returns WebServiceContext associated with this invocation.
    *
    * @param invocation current invocation
    * @return web service context or null if not available
    */
   private WebServiceContext getWebServiceContext(final Invocation invocation)
   {
      final InvocationContext invocationContext = invocation.getInvocationContext();

      return invocationContext.getAttachment(WebServiceContext.class);
   }

   /**
    * Returns endpoint instance associated with current invocation.
    *
    * @param invocation current invocation
    * @return target bean in invocation
    */
   private Object getTargetBean(final Invocation invocation)
   {
      final InvocationContext invocationContext = invocation.getInvocationContext();

      return invocationContext.getTargetBean();
   }

   private static final class ThreadLocalAwareWebServiceContext implements WebServiceContext
   {
      private static final ThreadLocalAwareWebServiceContext SINGLETON = new ThreadLocalAwareWebServiceContext();
      private final ThreadLocal<WebServiceContext> contexts = new InheritableThreadLocal<WebServiceContext>();

      private static ThreadLocalAwareWebServiceContext getInstance()
      {
         return SINGLETON;
      }

      private void setMessageContext(final WebServiceContext ctx)
      {
         this.contexts.set(ctx);
      }

      public EndpointReference getEndpointReference(Element... referenceParameters)
      {
         final WebServiceContext delegee = this.contexts.get();
         return delegee == null ? null : delegee.getEndpointReference(referenceParameters);
      }

      public <T extends EndpointReference> T getEndpointReference(Class<T> clazz, Element... referenceParameters)
      {
         final WebServiceContext delegee = this.contexts.get();
         return delegee == null ? null : delegee.getEndpointReference(clazz, referenceParameters);
      }

      public MessageContext getMessageContext()
      {
         final WebServiceContext delegee = this.contexts.get();
         return delegee == null ? null : delegee.getMessageContext();
      }

      public Principal getUserPrincipal()
      {
         final WebServiceContext delegee = this.contexts.get();
         return delegee == null ? null : delegee.getUserPrincipal();
      }

      public boolean isUserInRole(String role)
      {
         final WebServiceContext delegee = this.contexts.get();
         return delegee == null ? false : delegee.isUserInRole(role);
      }
   }

}
