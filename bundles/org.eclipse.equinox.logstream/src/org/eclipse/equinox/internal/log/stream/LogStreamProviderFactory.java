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
	
	public void postLogEntry(LogEntry entry) {
		// Reader of providers map
		// 1) for each provider
		// - post entry to provider
		
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
	
	
	@Override
	public LogStreamProviderImpl getService(Bundle bundle, ServiceRegistration<LogStreamProvider> registration) {
		// Writer to providers map
		// 1) create new LogStreamProviderImpl
		// 2) put new instance in map
		// 3) return new instance
		
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

	@Override
	public void ungetService(Bundle bundle, ServiceRegistration<LogStreamProvider> registration,
			LogStreamProvider service) {
		
		//1) Remove the logStreamProviderImpl instance associated with the bundle
		//2) close all existing LogStreams from the provider, outside the write lock
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
