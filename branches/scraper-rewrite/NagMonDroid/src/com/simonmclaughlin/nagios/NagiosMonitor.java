package com.simonmclaughlin.nagios;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.simonmclaughlin.nagios.service.IRemoteService;
import com.simonmclaughlin.nagios.service.IRemoteServiceCallback;
import com.simonmclaughlin.nagios.service.ISecondary;

public class NagiosMonitor extends ListActivity {
	 /** The primary interface we will be calling on the service. */
    IRemoteService mService = null;
    /** Another interface we use on the service. */
    ISecondary mSecondaryService = null;

    private boolean mIsBound;
    private SharedPreferences app_preferences;
    private boolean first_run;
    private String previous_xml;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Here is where we request the application preferences
        app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Now, we're going to check for the 'first_run' variable
        first_run = app_preferences.getBoolean("first_run", true);
        Boolean service_started = app_preferences.getBoolean("service_started", false);
        previous_xml = app_preferences.getString("previous_xml", "");
        
        // If it's false (the default) then we need to display an alert dialog
        if (first_run) {
                new AlertDialog.Builder(NagiosMonitor.this)
                .setTitle(getString(R.string.first_run_title))
                .setMessage(getString(R.string.first_run_message))
                .setNeutralButton(getString(R.string.close), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                                SharedPreferences.Editor editor = app_preferences.edit();
                                        editor.putBoolean("first_run", false);
                                        editor.commit();
                        }
                })
                .show();
        } 
        if(service_started)
        {
        	TextView topMsg = (TextView)this.findViewById( R.id.topmsg );
        	topMsg.setText(getString(R.string.background_enabled)); 
        	startService(new Intent(
            "com.simonmclaughlin.nagios.service.REMOTE_SERVICE"));
	    	SharedPreferences.Editor editor = app_preferences.edit();
            editor.putBoolean("service_started", true);
            editor.commit();
        	bindService(new Intent(IRemoteService.class.getName()),
                    mConnection, Context.BIND_AUTO_CREATE);
            bindService(new Intent(ISecondary.class.getName()),
                    mSecondaryConnection, Context.BIND_AUTO_CREATE);
            mIsBound = true;
        }
        if(!previous_xml.equals(""))
        {
        	try 
			{ 
				XMLParserHandler parser = new XMLParserHandler();
				parser.parse(previous_xml);
				ArrayList<Status> data = parser.getData();
				displayStatus(data);
        
			} catch (Exception e) {
				/* Display any Error to the GUI. */
			}
        }
    }
       
    /**
    * {@inheritDoc}
    */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
    	super.onCreateOptionsMenu(menu);

    	MenuInflater inflater = new MenuInflater(this);
    	inflater.inflate(R.menu.menu, menu);
    	// Return true so that the menu gets displayed.
    	return true;   
    }      
    
    /**
    * {@inheritDoc}
    */
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	if (item.hasSubMenu() == false)
    	{
    		if(item.getTitle().equals(getString(R.string.menu_settings)))
    		{
    			Intent i_settings = new Intent(NagiosMonitor.this, Preferences.class);
    			startActivity(i_settings);
    		}
    		else if(item.getTitle().equals(getString(R.string.menu_start)))
    		{
    			try {

    			    // setup and start Service
    			    {
    			    	startService(new Intent(
                        "com.simonmclaughlin.nagios.service.REMOTE_SERVICE"));
    			    	SharedPreferences.Editor editor = app_preferences.edit();
                        editor.putBoolean("service_started", true);
                        editor.commit();
                        TextView topMsg = (TextView)this.findViewById( R.id.topmsg );
                    	topMsg.setText(getString(R.string.background_enabled)); 
    			    	
    			    }

    			  }
    			  catch (Exception e) {
    			    Log.e("Start", "ui creation problem", e);
    			  }
    			  bindService(new Intent(IRemoteService.class.getName()),
		                    mConnection, Context.BIND_AUTO_CREATE);
		            bindService(new Intent(ISecondary.class.getName()),
		                    mSecondaryConnection, Context.BIND_AUTO_CREATE);
		            mIsBound = true;

    		}
    		else if(item.getTitle().equals(getString(R.string.menu_stop)))
    		{
    			if (mIsBound) 
    			{
	                // If we have received the service, and hence registered with
	                // it, then now is the time to unregister.
	                if (mService != null) {
	                    try {
	                        mService.unregisterCallback(mCallback);
	                    } catch (RemoteException e) {
	                        // There is nothing special we need to do if the service
	                        // has crashed.
	                    }
	                }
	                
	                // Detach our existing connection.
	                unbindService(mConnection);
	                unbindService(mSecondaryConnection);
	               
	                mIsBound = false;
	                TextView topMsg = (TextView)this.findViewById( R.id.topmsg );
	                topMsg.setText(getString(R.string.background_disabled)); 
	            }
    			try {

    			    // setup and start Service
    			    {
    			    	stopService(new Intent(
                        "com.simonmclaughlin.nagios.service.REMOTE_SERVICE"));
    			    	SharedPreferences.Editor editor = app_preferences.edit();
                        editor.putBoolean("service_started", false);
                        editor.commit();
    			    }

    			  }
    			  catch (Exception e) 
    			  {
    			    Log.e("Stop", "ui creation problem", e);
    			  }
    		}
    		else if(item.getTitle().equals(getString(R.string.menu_about)))
    		{
    			 PackageManager pm = getPackageManager();
    		            //---get the package info---
    		            PackageInfo pi = null;
						try {
							pi = pm.getPackageInfo("com.simonmclaughlin.nagios", 0);
						} catch (NameNotFoundException e) {
							e.printStackTrace();
						}

    			new AlertDialog.Builder(NagiosMonitor.this)
                .setTitle(getString(R.string.about_title))
                .setMessage(getString(R.string.about_message)+" (http://www.simonmclaughlin.co.uk)\n"+getString(R.string.version)+": "+pi.versionName)
                .setNeutralButton(getString(R.string.close), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                                SharedPreferences.Editor editor = app_preferences.edit();
                                        editor.putBoolean("first_run", false);
                                        editor.commit();
                        }
                })
                .show();
    		}
    	}
       
        // Consume the selection event.
        return true;
    }   

    public void displayStatus(ArrayList<Status> data)
    {
    	
    	StatusAdapter statusAdapter = new StatusAdapter( 
    			this,
				R.layout.statusrow,
				data); 
    	
    	setListAdapter( statusAdapter ); 	
    } 
    
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = IRemoteService.Stub.asInterface(service);

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
            
            // As part of the sample, tell the user what happened.
            Toast.makeText(NagiosMonitor.this, R.string.remote_service_connected,
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;

            // As part of the sample, tell the user what happened.
            Toast.makeText(NagiosMonitor.this, R.string.remote_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * Class for interacting with the secondary interface of the service.
     */
    private ServiceConnection mSecondaryConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // Connecting to a secondary interface is the same as any
            // other interface.
            mSecondaryService = ISecondary.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            mSecondaryService = null;
        }
    };
    
    // ----------------------------------------------------------------------
    // Code showing how to deal with callbacks.
    // ----------------------------------------------------------------------
    
    /**
     * This implementation is used to receive callbacks from the remote
     * service.
     */
    private IRemoteServiceCallback mCallback = new IRemoteServiceCallback.Stub() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */

		public void valueChanged(String value) throws RemoteException {
			 mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, value));
			
		}
    };
    
    private static final int BUMP_MSG = 1;
    
    private Handler mHandler = new Handler() {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
                case BUMP_MSG:
                	if(msg.obj != null)
                	{
                		String xml = msg.obj.toString();
                		if(!previous_xml.equals(xml))
                		{
                			try 
                			{ 
                				XMLParserHandler parser = new XMLParserHandler();
                				parser.parse(xml);
                				ArrayList<Status> data = parser.getData();
                				displayStatus(data);
                				previous_xml = xml;
                				SharedPreferences.Editor editor = app_preferences.edit();
                                editor.putString("previous_xml", previous_xml);
                                editor.commit();
                			} catch (Exception e) {
                				/* Display any Error to the GUI. */
                			}
                		}
                	}
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
        
    };
}
