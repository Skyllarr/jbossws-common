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
package org.jboss.ws.common.invocation;

import java.lang.reflect.Method;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.ws.common.Loggers;
import org.jboss.wsf.spi.deployment.Endpoint;
import org.jboss.wsf.spi.invocation.Invocation;

/**
 * Handles invocations on JSE endpoints.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:tdiesler@redhat.com">Thomas Diesler</a>
 */
public abstract class AbstractInvocationHandlerJSE extends AbstractInvocationHandler
{
   private static final String POJO_JNDI_PREFIX = "java:comp/env/";

   private volatile boolean initialized;

   /**
    * Constructor.
    */
   protected AbstractInvocationHandlerJSE()
   {
      super();
   }

   private void init(final Endpoint endpoint, final Invocation invocation)
   throws Exception
   {
      if (!initialized)
      {
         synchronized(this)
         {
            if (!initialized)
            {
               onEndpointInstantiated(endpoint, invocation);
               initialized = true;
            }
         }
      }
   }

   /**
    * Invokes method on endpoint implementation.
    *
    * This method does the following steps:
    *
    * <ul>
    *   <li>lookups endpoint implementation method to be invoked,</li>
    *   <li>
    *     notifies all subclasses about endpoint method is going to be invoked<br/>
    *     (using {@link #onBeforeInvocation(Invocation)} template method),  
    *   </li>
    *   <li>endpoint implementation method is invoked,</li>
    *   <li>
    *     notifies all subclasses about endpoint method invocation was completed<br/>
    *     (using {@link #onAfterInvocation(Invocation)} template method).  
    *   </li>
    * </ul>
    *
    * @param endpoint which method is going to be invoked
    * @param invocation current invocation
    * @throws Exception if any error occurs
    */
   public final void invoke(final Endpoint endpoint, final Invocation invocation) throws Exception
   {
      try
      {
         // prepare for invocation
         this.init(endpoint, invocation);
         final Object targetBean = invocation.getInvocationContext().getTargetBean();
         final Class<?> implClass = targetBean.getClass();
         final Method seiMethod = invocation.getJavaMethod();
         final Method implMethod = this.getImplMethod(implClass, seiMethod);
         final Object[] args = invocation.getArgs();

         // notify subclasses
         this.onBeforeInvocation(invocation);

         // invoke implementation method
         final Object retObj = implMethod.invoke(targetBean, args);

         // set invocation result
         invocation.setReturnValue(retObj);
      }
      catch (Exception e)
      {
         Loggers.ROOT_LOGGER.methodInvocationFailed(e);
         // propagate exception
         this.handleInvocationException(e);
      }
      finally
      {
         // notify subclasses
         this.onAfterInvocation(invocation);
      }
   }

   @Override
   public Context getJNDIContext(final Endpoint ep) throws NamingException
   {
      return (Context) new InitialContext().lookup(POJO_JNDI_PREFIX);
   }

}
