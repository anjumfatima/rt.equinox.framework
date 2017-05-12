package org.eclipse.equinox.internal.log.stream;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.stream.LogStreamProvider;
import org.osgi.util.pushstream.PushEvent;
import org.osgi.util.pushstream.PushStream;
import org.osgi.util.pushstream.PushStreamBuilder;
import org.osgi.util.pushstream.PushStreamProvider;
import org.osgi.util.tracker.ServiceTracker;

public class LogStreamProviderImpl implements LogStreamProvider {
	private final PushStreamProvider pushStreamProvider = new PushStreamProvider();
	private final ServiceTracker<LogReaderService, AtomicReference<LogReaderService>> logReaderService;
	private final WeakHashMap<LogEntrySource,Boolean> weakMap = new WeakHashMap<>();
	private final Set<LogEntrySource> logEntrySources = Collections.newSetFromMap(weakMap);

	private final ReentrantReadWriteLock historyLock = new ReentrantReadWriteLock();
	
	private final ExecutorService executor = Executors.newFixedThreadPool(1, new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, "LogStream thread");
		}
	});
	
	public LogStreamProviderImpl(
			ServiceTracker<LogReaderService, AtomicReference<LogReaderService>> logReaderService) {
		this.logReaderService = logReaderService;
	}

	@Override
	public PushStream<LogEntry> createStream(Options... options) {
		ServiceTracker<LogReaderService, AtomicReference<LogReaderService>> withHistory = null;
		if (options != null) {
			for (Options option : options) {
				if (Options.HISTORY.equals(option)) {
					withHistory = logReaderService;
				}
			}
		}
		
		BlockingQueue<PushEvent<? extends LogEntry>> buffer = new LinkedBlockingQueue<>();
		
		historyLock.writeLock().lock();
			try{
				LogEntrySource logEntrySource = new LogEntrySource(withHistory);
				PushStreamBuilder<LogEntry, BlockingQueue<PushEvent<? extends LogEntry>>> streamBuilder = pushStreamProvider.buildStream(logEntrySource);
				//withBuffer method is used to create the stream to handle the traffic coming from the source
				PushStream<LogEntry> logStream = streamBuilder.withBuffer(buffer).withExecutor(executor).create();
				logEntrySource.setLogStream(logStream);
				// Adding to sources makes the source start listening for new entries
				logEntrySources.add(logEntrySource);
				return logStream;
			}
			finally{
				historyLock.writeLock().unlock();
			}
	}

	public void logged(LogEntry entry) {
		historyLock.readLock().lock();
		try{
			for (LogEntrySource logEntrySource : logEntrySources) {
				logEntrySource.logged(entry);
			}
		}
		finally{
			historyLock.readLock().unlock();
		}
	}
	
	
	
	public void close() {
		PushStream<LogEntry> logStream;
		historyLock.readLock().lock();
		try{
			for(LogEntrySource logEntrySource : logEntrySources) {
			    logStream = logEntrySource.getLogStream();
				try {
					logStream.close();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			executor.shutdown();
		
		}finally{
			historyLock.readLock().unlock();
		}
	}	

}
