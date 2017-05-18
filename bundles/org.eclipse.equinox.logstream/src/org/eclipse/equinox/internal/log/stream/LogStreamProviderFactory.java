/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.log.stream;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.stream.LogStreamProvider;
import org.osgi.util.tracker.ServiceTracker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

public class LogStreamProviderFactory implements ServiceFactory<LogStreamProvider> {
	
	Map<Bundle, LogStreamProviderImpl> providers = new HashMap<Bundle,LogStreamProviderImpl>() ;
	ReentrantReadWriteLock eventProducerLock = new ReentrantReadWriteLock();
	ServiceTracker<LogReaderService, AtomicReference<LogReaderService>> logReaderService;

	public LogStreamProviderFactory(ServiceTracker<LogReaderService, AtomicReference<LogReaderService>> logReaderService){
		this.logReaderService = logReaderService;
	}
	
	
	/*Reader of providers map
 	 *	1) for each provider
	 *		- post entry to provider
	 */
	public void postLogEntry(LogEntry entry) {
		eventProducerLock.readLock().lock();
		try{
			for(LogStreamProviderImpl provider : providers.values()){
				provider.logged(entry);
			}
		}
		finally{
			eventProducerLock.readLock().unlock();
		}
		
	}
	
	/* Writer to providers map
	 * 1) create new LogStreamProviderImpl
	 * 2) put new instance in map
	 * 3) return new instance
	 * (non-Javadoc)
	 * @see org.osgi.framework.ServiceFactory#getService(org.osgi.framework.Bundle, org.osgi.framework.ServiceRegistration)
	 */
	
	@Override
	public LogStreamProviderImpl getService(Bundle bundle, ServiceRegistration<LogStreamProvider> registration) {
		LogStreamProviderImpl logStreamProviderImpl = new LogStreamProviderImpl(logReaderService);
		eventProducerLock.writeLock().lock();
		try{
			providers.put(bundle, logStreamProviderImpl);
			return logStreamProviderImpl;
		}
		finally{
			eventProducerLock.writeLock().unlock();
		}
	}
	
	/* 1) Remove the logStreamProviderImpl instance associated with the bundle
	 * 2) close all existing LogStreams from the provider, outside the write lock
	 * (non-Javadoc)
	 * @see org.osgi.framework.ServiceFactory#ungetService(org.osgi.framework.Bundle, org.osgi.framework.ServiceRegistration, java.lang.Object)
	 */

	@Override
	public void ungetService(Bundle bundle, ServiceRegistration<LogStreamProvider> registration,
			LogStreamProvider service) {

		LogStreamProviderImpl logStreamProviderImpl;
		
		eventProducerLock.writeLock().lock();
		try{
			logStreamProviderImpl = providers.remove(bundle);
		}
		finally{
			eventProducerLock.writeLock().unlock();
		}
		
		logStreamProviderImpl.close();
		 
	}

	

	

}
