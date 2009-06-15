package com.parc.ccn.network.daemons.repo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

import javax.xml.stream.XMLStreamException;

import com.parc.ccn.Library;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.query.CCNInterestListener;
import com.parc.ccn.data.query.Interest;
import com.parc.ccn.library.CCNLibrary;
import com.parc.ccn.library.profiles.SegmentationProfile;

/**
 * Handle incoming data in the repository. Currently only handles
 * the stream "shape"
 * 
 * @author rasmusse
 *
 */

public class RepositoryDataListener implements CCNInterestListener {
	private long _timer;
	private Interest _origInterest;
	private TreeMap<ContentName, Interest> _interests = new TreeMap<ContentName, Interest>();
	private boolean _haveHeader = false;
	private boolean _sentHeaderInterest = false;
	private Interest _headerInterest = null;	
	private RepositoryDaemon _daemon;
	private CCNLibrary _library;
	private long _currentBlock = 0;
	
	/**
	 * So the main listener can output interests sooner, we do the data creation work
	 * in a separate thread.
	 * 
	 * @author rasmusse
	 *
	 */
	private class DataHandler implements Runnable {
		private ContentObject _content;
		
		private DataHandler(ContentObject co) {
			Library.logger().info("Saw data: " + co.name());
			_content = co;
		}
	
		public void run() {
			try {
				Library.logger().finer("Saving content in: " + _content.name().toString());
				_daemon.getRepository().saveContent(_content);		
				if (_daemon.getRepository().checkPolicyUpdate(_content)) {
					_daemon.resetNameSpaceFromHandler();
				}
			} catch (RepositoryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public RepositoryDataListener(Interest origInterest, Interest interest, RepositoryDaemon daemon) throws XMLStreamException, IOException {
		_origInterest = interest;
		_daemon = daemon;
		_library = daemon.getLibrary();
		_timer = new Date().getTime();
	}
	
	public Interest handleContent(ArrayList<ContentObject> results,
			Interest interest) {
		
		_timer = new Date().getTime();
		
		for (ContentObject co : results) {
			_daemon.getThreadPool().execute(new DataHandler(co));
			
			synchronized (this) {
				if (!_haveHeader) {
					/*
					 * Handle headers specifically. If we haven't seen one yet ask for it specifically
					 */
					if (SegmentationProfile.isUnsegmented(co.name())) {
						_haveHeader = true;
					} else {
						if (!_sentHeaderInterest) {
							_headerInterest = new Interest(SegmentationProfile.segmentRoot(co.name()));
							_headerInterest.additionalNameComponents(1);
							try {
								_library.expressInterest(_headerInterest, this);
								_sentHeaderInterest = true;
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
			}
				
			if (SegmentationProfile.isSegment(co.name())) {
				long thisBlock = SegmentationProfile.getSegmentNumber(co.name());
				if (thisBlock >= _currentBlock)
					_currentBlock = thisBlock + 1;
				synchronized (_interests) {
					_interests.remove(co.name());
				}
			}
			
			/*
			 * Compute next interests to ask for and ask for them
			 */
			synchronized (_interests) {
				long firstInterestToRequest = _interests.size() > 0 
						? SegmentationProfile.getSegmentNumber(_interests.lastKey()) + 1
						: _currentBlock;
				int nOutput = _interests.size() >= _daemon.getWindowSize() ? 0 : _daemon.getWindowSize() - _interests.size();
	
				for (int i = 0; i < nOutput; i++) {
					ContentName name = SegmentationProfile.segmentName(co.name(), firstInterestToRequest + i);
					Interest newInterest = new Interest(name);
					try {
						_library.expressInterest(newInterest, this);
						_interests.put(name, newInterest);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		return null;
	}
	
	public void cancelInterests() {
		for (ContentName name : _interests.keySet())
			_library.cancelInterest(_interests.get(name), this);
		_library.cancelInterest(_headerInterest, this);
	}
	
	public long getTimer() {
		return _timer;
	}
	
	public void setTimer(long time) {
		_timer = time;
	}
	
	public Interest getOrigInterest() {
		return _origInterest;
	}
}
