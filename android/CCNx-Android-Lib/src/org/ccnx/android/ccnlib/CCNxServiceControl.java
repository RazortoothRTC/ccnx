/*
 * CCNx Android Helper Library.
 *
 * Copyright (C) 2010-2012 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation. 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ccnx.android.ccnlib;

import org.ccnx.android.ccnlib.CCNxServiceStatus.SERVICE_STATUS;
import org.ccnx.android.ccnlib.CcndWrapper.CCND_OPTIONS;
import org.ccnx.android.ccnlib.RepoWrapper.REPO_OPTIONS;
import org.ccnx.android.ccnlib.RepoWrapper.CCNS_OPTIONS;
import org.ccnx.android.ccnlib.RepoWrapper.CCNR_OPTIONS;
import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.impl.security.keys.BasicKeyManager;
import org.ccnx.ccn.utils.ccndcontrol;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.config.UserConfiguration;

import android.content.Context;
import android.util.Log;
import android.os.Environment;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This is a helper class to access the ccnd and repo services. It provides
 * abstractions so that the programs can start and stop the services as well
 * as interact with them for configuration and monitoring.
 */
public final class CCNxServiceControl {
	public final static Long MINIMUM_SECONDS_SINCE_EPOCH = 946684800L;
	private final static String TAG = "CCNxServiceControl";
	private static String mErrorMessage = "";

	CcndWrapper ccndInterface;
	RepoWrapper repoInterface;
	Context _ctx;
	
	CCNxServiceCallback _cb = null;
	
	SERVICE_STATUS ccndStatus = SERVICE_STATUS.SERVICE_OFF;
	SERVICE_STATUS repoStatus = SERVICE_STATUS.SERVICE_OFF;
	
	CCNxServiceCallback ccndCallback = new CCNxServiceCallback(){
		@Override
		public void newCCNxStatus(SERVICE_STATUS st) {
			Log.i(TAG,"ccndCallback ccndStatus = " + st.toString());
			ccndStatus = st;
			switch(ccndStatus){
			case SERVICE_OFF:
				newCCNxAPIStatus(SERVICE_STATUS.CCND_OFF);
				break;
			case SERVICE_INITIALIZING:
				newCCNxAPIStatus(SERVICE_STATUS.CCND_INITIALIZING);
				break;
			case SERVICE_TEARING_DOWN:
				newCCNxAPIStatus(SERVICE_STATUS.CCND_TEARING_DOWN);
				break;
			case SERVICE_RUNNING:
				newCCNxAPIStatus(SERVICE_STATUS.CCND_RUNNING);
				break;
			case SERVICE_ERROR:
				newCCNxAPIStatus(SERVICE_STATUS.SERVICE_ERROR);
				break;
			default:
				Log.d(TAG, "ccndCallback, ignoring status = " + st.toString());
			}
		}
	};
	
	CCNxServiceCallback repoCallback = new CCNxServiceCallback(){
		@Override
		public void newCCNxStatus(SERVICE_STATUS st) {
			Log.i(TAG,"repoCallback repoStatus = " + st.toString());
			repoStatus = st;	
			switch(repoStatus){
			case SERVICE_OFF:
				newCCNxAPIStatus(SERVICE_STATUS.REPO_OFF);
				break;
			case SERVICE_INITIALIZING:
				newCCNxAPIStatus(SERVICE_STATUS.REPO_INITIALIZING);
				break;
			case SERVICE_TEARING_DOWN:
				newCCNxAPIStatus(SERVICE_STATUS.REPO_TEARING_DOWN);
				break;
			case SERVICE_RUNNING:
				newCCNxAPIStatus(SERVICE_STATUS.REPO_RUNNING);
				break;
			case SERVICE_ERROR:
				newCCNxAPIStatus(SERVICE_STATUS.SERVICE_ERROR);
				break;
			default:
				Log.d(TAG, "repoCallback, ignoring status = " + st.toString());
			}
		}
	};
	
	public CCNxServiceControl(Context ctx) {
		_ctx = ctx;
		ccndInterface = new CcndWrapper(_ctx);
		ccndInterface.setCallback(ccndCallback);
		ccndStatus = ccndInterface.getStatus();
		repoInterface = new RepoWrapper(_ctx);
		repoInterface.setCallback(repoCallback);
		repoStatus = repoInterface.getStatus();
		
	}
	
	public void registerCallback(CCNxServiceCallback cb){
		_cb = cb;
	}
	
	public void unregisterCallback(){
		_cb = null;
	}
	
	/**
	 * Start the CCN daemon and Repo 
	 * If configuration parameters have been set these will be used
	 * This is a BLOCKING call
	 * 
	 * @return true if everything started correctly, false otherwise
	 */
	public boolean startAll(){
		Log.d(TAG, "startAll()");
		if (checkSystemOK()) {
			newCCNxAPIStatus(SERVICE_STATUS.START_ALL_INITIALIZING);
			Log.i(TAG,"startAll waiting for CCND startService");
			ccndInterface.startService();
			Log.i(TAG,"startAll waiting for CCND waitForReady");
			ccndInterface.waitForReady();
			newCCNxAPIStatus(SERVICE_STATUS.START_ALL_CCND_DONE);
			if(!ccndInterface.isReady()){
				mErrorMessage = mErrorMessage.concat("Unable to start ccnd service.");
				newCCNxAPIStatus(SERVICE_STATUS.START_ALL_ERROR);
				return false;
			}
			// Go ahead and set default forwarding
			new AsyncDefaultForwardingConfigTask().execute();

			Log.i(TAG,"startAll waiting for REPO startService");
			repoInterface.startService();
			Log.i(TAG,"startAll waiting for REPO waitForReady");
			repoInterface.waitForReady();
			newCCNxAPIStatus(SERVICE_STATUS.START_ALL_REPO_DONE);
			if(!repoInterface.isReady()){
				mErrorMessage = mErrorMessage.concat("Unable to start repo service.");
				newCCNxAPIStatus(SERVICE_STATUS.START_ALL_ERROR);
				return false;
			} 
			newCCNxAPIStatus(SERVICE_STATUS.START_ALL_DONE);
			return true;
		} else {
			newCCNxAPIStatus(SERVICE_STATUS.START_ALL_ERROR);
			return false;
		}

	}
	
	/**
	 * Start the CCN daemon and Repo 
	 * If configuration parameters have been set these will be used
	 * This is a non-blocking call.  If you want to be notified when everything
	 * has started then you should register a callback before issuing this call.
	 */
	public void startAllInBackground(){
		Log.d(TAG, "startAllInBackground");
		Runnable r = new Runnable(){
			public void run() {
				startAll();
			}
		};
		Thread thd = new Thread(r);
		thd.start();
	}
	
	public void connect(){
		ccndInterface.bindIfRunning();
		repoInterface.bindIfRunning();
	}
	
	/**
	 * Disconnect from the services.  This is needed for a clean exit from an application. It leaves the services running.
	 */
	public void disconnect(){
		ccndInterface.unbindService();
		repoInterface.unbindService();
	}
	
	/**
	 * Stop the CCN daemon and Repo 
	 * This call will unbind from the service and stop it. There is no need to issue a disconnect().
	 */
	public void stopAll(){
		repoInterface.stopService();
		ccndInterface.stopService();
		newCCNxAPIStatus(SERVICE_STATUS.STOP_ALL_DONE);
	}

	public boolean checkSystemOK() {
		//
		// Do a quick check of things before we start.  If we can't properly initialize the following, don't pass go.
		// We fail right away rather than checking everything.
		// 1) system time
		// 2) check external storage writable
		// 3) other checks - TBD ... In the future we may want to verify that we have at least one usable *face
		//
		Log.d(TAG, "Checking current time in millis: " + System.currentTimeMillis() + " and date today = " + new java.util.Date());
		if (System.currentTimeMillis()/1000 < MINIMUM_SECONDS_SINCE_EPOCH) {
			// Realistically no modern device will be shipping from the factory without a reasonable default
			// near or close to the current date at manufacture, nor will it lack the ability to get time
			// from the network.  However, in dealing with Android "open source", some devices still seem to 
			// ship with time set to the beginning of the epoch, i.e., 0.
			Log.e(TAG,"Error in checkSystemOK(), please set OS System Time to valid, non-default date.");
			mErrorMessage = mErrorMessage.concat("Please set OS System Time before running this service.");
			return false;
		}

		if (!Environment.getExternalStorageDirectory().canWrite()) {
			// Again, not a likely scenario, but it's been seen before that some Android devices have either 
			// low quality media or problems in the design of the SDCARD reader that prevent the external storage
			// from build a valid write target.  Since we'll need both access to this storage and write 
			// access to it, we should not proceed if we fail to get writable external storage.
			// Future, more robust versions of this service should look for alternatives (app data space)
			// before failing completely.
			Log.e(TAG,"Error in checkSystemOK(), please fix permissions to access external storage for write, or insert writable media.");
			mErrorMessage = mErrorMessage.concat("Please check external SDCARD is available and writable.");
			return false;
		}

		return true;
	}
	public boolean isCcndRunning(){
		return ccndInterface.isRunning();
	}
	
	public boolean isRepoRunning(){
		return repoInterface.isRunning();
	}
	
	public void startCcnd(){
		ccndInterface.startService();
	}
	
	public void stopCcnd(){
		ccndInterface.stopService();
	}
	
	public void startRepo(){
		repoInterface.startService();
	}
	
	public void stopRepo(){
		repoInterface.stopService();
	}
	
	public void newCCNxAPIStatus(SERVICE_STATUS s){
		Log.d(TAG,"newCCNxAPIStatus sending " + s.toString());
		try {
			if(_cb != null) {
				_cb.newCCNxStatus(s);
			}
		} catch(Exception e){
			// Did the callback just throw an exception??
			// We're going to ignore it, it's not our problem (right?)
			Log.e(TAG,"The client callback has thrown an exception");
			e.printStackTrace();
		}
	}

	public void setCcndOption(CCND_OPTIONS option, String value) {
		ccndInterface.setOption(option, value);
	}
	
	public void setRepoOption(REPO_OPTIONS option, String value) {
		repoInterface.setOption(option, value);
	}
	
	public void setSyncOption(CCNS_OPTIONS option, String value) {
		repoInterface.setOption(option, value);
	}
	
	public void setCcnrOption(CCNR_OPTIONS option, String value) {
		repoInterface.setOption(option, value);
	}
	
	public String getErrorMessage() {
		return mErrorMessage;
	}

	public void clearErrorMessage() {
		mErrorMessage = "";
	}
	/**
	 * Are ccnd and the repo running and ready?
	 * @return true if BOTH ccnd and the repo are in state Running
	 */
	public boolean isAllRunning(){
		return(SERVICE_STATUS.SERVICE_RUNNING.equals(ccndStatus) &&
			   SERVICE_STATUS.SERVICE_RUNNING.equals(repoStatus));
	}
	
	public SERVICE_STATUS getCcndStatus(){
		return ccndStatus;
	}
	
	public SERVICE_STATUS getRepoStatus(){
		return repoStatus;
	}

	private class AsyncDefaultForwardingConfigTask extends AsyncTask<Void,Void,Void>{
	    @Override
	    protected Void doInBackground(Void... unused) {
	      Log.d(TAG, "configure default routes");
	      configureDefaultRoutes();
	        
	      return(null);
	    }

	    @Override
	    protected void onProgressUpdate(Void... unused) {
	      // setProgressPercent(0);
	    }
	    
	    @Override
	    protected void onPreExecute() {
	    }

	    @Override
	    protected void onPostExecute(Void unused) {
	    }
	}

	private KeyManager loadCCNDKeyManager() throws ConfigurationException, IOException {
		// Borrow a bunch of this code directly from CcndService
		char[] KEYSTORE_PASS = "\010\043\103\375\327\237\152\351\155".toCharArray();

		String ccnd_port = ccndInterface.getOption(CCND_OPTIONS.CCN_LOCAL_PORT.name());
		if( ccnd_port == null ) {
			ccnd_port = "9695";
		}

		String ccnd_keydir = ccndInterface.getOption(CCND_OPTIONS.CCND_KEYSTORE_DIRECTORY.name());
		if( ccnd_keydir == null ) {
			File f = _ctx.getDir("ccnd", Context.MODE_PRIVATE );
			ccnd_keydir = f.getAbsolutePath();
		}
		String keystore_name = ".ccnd_keystore_";

		// ccnd_keydir, KEYSTORE_NAME + ccnd_port
		// File dir = new File(dir_name);

		// File try_keystore = new File(ccnd_keydir, keystore_name);
		// FileOutputStream stream = new FileOutputStream(try_keystore);
		/* public BasicKeyManager(String userName, String keyStoreDirectory,
						   String configurationFileName,
						   String keyStoreFileName, String keyStoreType, 
						   String defaultAlias, char [] password) throws ConfigurationException, IOException {
		*/
		BasicKeyManager keyManager = new BasicKeyManager("CCND", ccnd_keydir, null, keystore_name + ccnd_port, null, "ccnd", KEYSTORE_PASS);
		// BasicKeyManager keyManager = new BasicKeyManager("user", "/mnt/sdcard/ccnx", null, null, null, null, "changeme".toCharArray()); // "Th1s1sn0t8g00dp8ssw0rd."
		
		Log.d(TAG, "ccnd_keydir = " + ccnd_keydir);
		Log.d(TAG, "keystore_name = " + keystore_name + ccnd_port);

		UserConfiguration.setUserConfigurationDirectory(ccnd_keydir); // XXX Hardcoded value
        UserConfiguration.setUserName("CCND"); // XXX Hardcoded value
        UserConfiguration.setKeystoreFileName(keystore_name + ccnd_port);
        UserConfiguration.setKeystorePassword("\010\043\103\375\327\237\152\351\155");
		Log.d(TAG, "loadCCNDKeyManager() succesfully called");
		// stream.close();
		return keyManager;
	}

	public int configureDefaultRoutes() {
    	int status = 0;
    	//
        // Let's try to create the routes if there are any set
        //
        final String defaultForwardingEntriesProp = ccndInterface.getOption(CCND_OPTIONS.CCND_DEFAULT_FORWARDING_ENTRIES.name());
        String defaultForwardingEntryStrings[] = defaultForwardingEntriesProp.split(",");
        // String keystoredir = ccndInterface.getOption(CCND_OPTIONS.CCND_KEYSTORE_DIRECTORY.name());
        // String keystorename = 

        //
        // Calls to ccndcontrol get an exception java.lang.NullPointerException: Attempt to invoke virtual method 'byte[] org.ccnx.ccn.protocol.PublisherPublicKeyDigest.digest()' on a null object reference
        //
        // UserConfiguration.setUserConfigurationDirectory("/mnt/sdcard/ccnx"); // XXX Hardcoded value
        // UserConfiguration.setUserName("user"); // XXX Hardcoded value

        if (defaultForwardingEntryStrings.length > 0) {
        	//
        	// Check each route, split into string parts
        	//
        	try {
        	
        		KeyManager keyManager = loadCCNDKeyManager();
        		if (keyManager != null) {
        			Log.d(TAG, "KeyManager is not NULL");
        		}
	        	for (int i = 0; i < defaultForwardingEntryStrings.length; i++) {
	        		final String[] forwardingcmd = defaultForwardingEntryStrings[i].split(" ");
	        		Log.d(TAG, "Splitting cmd: " + defaultForwardingEntryStrings[i] + " and attempt to configure route");
		            try {
		            	//
		            	// Set a route for each DEFAULT_FORWARDING_ENTRIES item
		            	//
		            	for (int j = 0; j < forwardingcmd.length; j++) {
		            		Log.d(TAG, "CMD[" + j + "] = " + forwardingcmd[j]);
		            	}
		            	// if (ccndcontrol.executeCommand(forwardingcmd, keyManager) < 0) {
		                if (ccndcontrol.executeCommand(forwardingcmd) < 0) {
		                    Log.e(TAG, "configureDefaultRoutes() Unable to configure forwarding for command because of internal error: " + defaultForwardingEntryStrings[0]);
		                } else {
		                	Log.d(TAG, "configureDefaultRoutes() success routing: " + defaultForwardingEntryStrings[0]);
		                	status--;
		                }
		            } catch(Exception e) { // XXX Should catch the actual exception
		                e.printStackTrace();
		                Log.e(TAG, "Unable to configure forwarding command, reason: " + e.getMessage());
		            }
	        	}
        	} catch(ConfigurationException ce) {
        		Log.e(TAG, "configureDefaultRoutes() failed with ConfigurationException: " + ce.getMessage());
        	} catch(IOException ioe) {
        		Log.e(TAG, "configureDefaultRoutes() failed with IOException, reason: " + ioe.getMessage());
        	}
        } else {
        	Log.d(TAG, "configureDefaultRoutes() routes are configured, nothing to do");
        }
        return status;
    }
}
