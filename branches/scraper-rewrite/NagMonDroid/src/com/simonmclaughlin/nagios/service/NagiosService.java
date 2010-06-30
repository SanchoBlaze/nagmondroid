package com.simonmclaughlin.nagios.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;

import com.simonmclaughlin.nagios.service.IRemoteService;
import com.simonmclaughlin.nagios.service.IRemoteServiceCallback;
import com.simonmclaughlin.nagios.service.ISecondary;
import com.simonmclaughlin.nagios.NagiosMonitor;
import com.simonmclaughlin.nagios.R;

public class NagiosService extends Service {
    /**
     * This is a list of callbacks that have been registered with the
     * service.  Note that this is package scoped (instead of private) so
     * that it can be accessed more efficiently from inner classes.
     */
    final RemoteCallbackList<IRemoteServiceCallback> mCallbacks
            = new RemoteCallbackList<IRemoteServiceCallback>();

    int mValue = 0;
    NotificationManager mNM;
    private SharedPreferences app_preferences;
    String data;
    private Timer timer = new Timer();
    private int interval_time = 0;
    private boolean service_start = false;

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.
        //showNotification("");
        app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String interval = app_preferences.getString("interval", "");
        //Log.i(getClass().getSimpleName(), interval);
        interval_time = Integer.parseInt(interval);
        timer.scheduleAtFixedRate(
    			new TimerTask() 
    			{
    				public void run() 
    				{
    					getNagiosStatus();
    				}
    			},
    		    0,
    		    interval_time);

        // While this service is running, it will continually increment a
        // number.  Send the first message that is used to perform the
        // increment.
        mHandler.sendEmptyMessage(REPORT_MSG);
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        //mNM.cancel(R.string.remote_service_started);
    	timer.cancel();
        // Tell the user we stopped.
        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show();

        // Unregister all callbacks.
        mCallbacks.kill();

        // Remove the next pending message to increment the counter, stopping
        // the increment loop.
        mHandler.removeMessages(REPORT_MSG);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Select the interface to return.  If your service only implements
        // a single interface, you can just return it here without checking
        // the Intent.
        if (IRemoteService.class.getName().equals(intent.getAction())) {
            return mBinder;
        }
        if (ISecondary.class.getName().equals(intent.getAction())) {
            return mSecondaryBinder;
        }
        return null;
    }

    /**
     * The IRemoteInterface is defined through IDL
     */
    private final IRemoteService.Stub mBinder = new IRemoteService.Stub() {
        public void registerCallback(IRemoteServiceCallback cb) {
            if (cb != null) mCallbacks.register(cb);
        }
        public void unregisterCallback(IRemoteServiceCallback cb) {
            if (cb != null) mCallbacks.unregister(cb);
        }
    };

    /**
     * A secondary interface to the service.
     */
    private final ISecondary.Stub mSecondaryBinder = new ISecondary.Stub() {
        public int getPid() {
            return Process.myPid();
        }
        public void basicTypes(int anInt, long aLong, boolean aBoolean,
                float aFloat, double aDouble, String aString) {
        }
    };

    private static final int REPORT_MSG = 1;

    /**
     * Our Handler used to execute operations on the main thread.  This is used
     * to schedule increments of our value.
     */
    private final Handler mHandler = new Handler() {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {

                // It is time to bump the value!
                case REPORT_MSG: {
                    // Broadcast to all clients the new value.
                    final int N = mCallbacks.beginBroadcast();
                    for (int i=0; i<N; i++) {
                        try {
                            mCallbacks.getBroadcastItem(i).valueChanged(data);
                        } catch (RemoteException e) {
                            // The RemoteCallbackList will take care of removing
                            // the dead object for us.
                        }
                    }
                    mCallbacks.finishBroadcast();

                    // Repeat every 1 second.
                    sendMessageDelayed(obtainMessage(REPORT_MSG), 1000);
                } break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    /**
     * Show a notification while this service is running.
     */
    private void showNotification(String message_text) 
    {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = message_text;

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.icon, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, NagiosMonitor.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, "NagMonDroid",
                       text, contentIntent);
        app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean vib = app_preferences.getBoolean("vibrate", false);
        if(vib)
        {
        	// after a 100ms delay, vibrate for 250ms, pause for 100 ms and
        	// then vibrate for 500ms.
        	notification.vibrate = new long[] { 100, 250, 100, 500};
        }
        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.remote_service_started, notification);
    }
    
    private AtomicBoolean httpRequestOustanding = new AtomicBoolean(false);
	
	 // Returns true if the HTTP request was started.
	 private boolean getNagiosStatus() 
	 {
		 Date now = new Date();
		 //Log.i(getClass().getSimpleName(), now.toGMTString());
		 app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
	        final String status_url = app_preferences.getString("status_url", "");
	        String user = app_preferences.getString("username", "");
	        String pass = app_preferences.getString("password", "");
	        final boolean displayok = app_preferences.getBoolean("displayok", false);
	        final boolean hidedisabled = app_preferences.getBoolean("hidedisabled", true);
	        
	    if (!httpRequestOustanding.compareAndSet(false, true)) 
	    {
	      return false;
	    }
	    final String urlBase = status_url;
	    final DefaultHttpClient client = new DefaultHttpClient();
	    client.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, pass));
	    
	    String url = urlBase + "?style=detail";
	    if(!displayok)
	    {
	    	url += "&servicestatustypes=28&hoststatustypes=15&serviceprops=0&hostprops=0";
	    }
	    	    
	    final HttpUriRequest request = new HttpGet(url);
	    Runnable httpRunnable = new Runnable() {
	      public void run() {
	        try {
	          client.execute(request, new ResponseHandler<HttpResponse>() {
	            public HttpResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
	              if (response.getStatusLine().getStatusCode() == 200) 
	              {
	            	  HttpEntity entity = response.getEntity();
	            	  InputStream input = null;
	            	  input = entity.getContent();
	            	  BufferedReader br = new BufferedReader(new InputStreamReader(input), 2048);
	                  StringBuilder sb = new StringBuilder();
	                  String line = null;
	                  while ((line = br.readLine()) != null) {
	                      sb.append(line + "\n");
	                  }
	                  br.close();
	                  int count = 0;
	                  int warningCount = 0;
	                  int unknownCount = 0;
	                  int criticalCount = 0;
	                  int okCount = 0;
	                  
	                  String hostName = null;
	                  String serviceName = null;
	                  String hostStatus = null;
	                  Boolean hostDisabled = false;
	                  Boolean serviceDisabled = false;
	                  String serviceStatus = null;
	                  String serviceDetails = null;
	                  
	                  Pattern Regex = Pattern.compile("<td align=left valign=center class='status(.*?)'><a href='extinfo\\.cgi\\?type=1&host=(.*?)' .+>(.*?)</a></td>", Pattern.CASE_INSENSITIVE);
	                  Matcher RegexMatcher = Regex.matcher(sb.toString());
	                  data = "<nagios>";
	                  while (RegexMatcher.find()) 
	                  {
	                	  hostName = RegexMatcher.group(2);
	                	  hostStatus = RegexMatcher.group(1);
	                	  
	                	  hostDisabled = false;
	                	  Pattern Regex1 = Pattern.compile("<A HREF='extinfo\\.cgi\\?type=1&host="+hostName+"'><IMG", Pattern.CASE_INSENSITIVE);
	                	  Matcher RegexMatcher1 = Regex1.matcher(sb.toString());
	                	  if (RegexMatcher1.find()) 
	                	  {
	                		  hostDisabled = true;
	                	  }
	                	  
	                	  if(!hostDisabled || !hidedisabled)
	                	  {
	                		  Pattern Regex2 = Pattern.compile("<A HREF='extinfo\\.cgi\\?type=2&host="+hostName+"&service=.*?'>(.*?)</A></TD></TR>.*?<td></td>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	                		  Matcher RegexMatcher2 = Regex2.matcher(sb.toString());
	                		  while(RegexMatcher2.find()) 
	                		  {
	                			  serviceDisabled = false;
	                			  Pattern Regex3 = Pattern.compile("disabled", Pattern.CASE_INSENSITIVE);
		                		  Matcher RegexMatcher3 = Regex3.matcher(RegexMatcher2.group(0));
		                		  if (RegexMatcher3.find()) 
			                	  {
			                		  serviceDisabled = true;
			                	  }
		                		  
		                		  if(!serviceDisabled || !hidedisabled)
			                	  {
		                			  serviceName = RegexMatcher2.group(1);
	                			  
		                			  Pattern Regex4 = Pattern.compile("<TD CLASS='status.*?>(.*?)</TD>", Pattern.CASE_INSENSITIVE);
		                			  Matcher RegexMatcher4 = Regex4.matcher(RegexMatcher2.group(0));
		                			  int tempCount = 1;
		                			  while (RegexMatcher4.find()) 
		                			  {
		                				  if(tempCount == 1)
		                				  {
		                					  serviceStatus = RegexMatcher4.group(1);
		                					  if(serviceStatus.equalsIgnoreCase("ok"))
		                					  {
		                						  okCount++;
		                					  }
		                					  if(serviceStatus.equalsIgnoreCase("warning"))
		                					  {
		                						  warningCount++;
		                					  }
		                					  if(serviceStatus.equalsIgnoreCase("unknown"))
		                					  {
		                						  unknownCount++;
		                					  }
		                					  if(serviceStatus.equalsIgnoreCase("critical"))
		                					  {
		                						  criticalCount++;
		                					  }
		                				  }
		                				  else if(tempCount == 5)
		                				  {
		                					  serviceDetails = RegexMatcher4.group(1);
		                				  }
		                				  tempCount++;
		                			  } 
		                			  data += "<problem><host>"+hostName+"</host><service>"+serviceName+"</service><level>"+serviceStatus+"</level><info>"+Html.fromHtml(serviceDetails)+"</info><nagios>"+status_url+"</nagios></problem>";		                			 
		                			  count++;
			                	  }
	                		  }
	                		  
	                		  
	                	  }
	                  }               
	                  if(data.equals("<nagios>"))
	                  {
	                	  data += "<problem><host>"+getString(R.string.all_ok)+"</host><service>"+getString(R.string.yay)+"</service><level>ok</level><info>"+getString(R.string.all_good)+"</info><nagios>"+status_url+"</nagios></problem>"; 
	                  }
	                  data += "</nagios>";
	                  String msg = "";
	                  
	                  if(criticalCount != 0)
	                  {
		                  if(criticalCount == 1)
		                  {
		                	  msg += criticalCount+" "+getString(R.string.notification_critical_service)+" ";
		                  }
		                  else
		                  {
		                	  msg += criticalCount+" "+getString(R.string.notification_critical_services)+" ";
		                  }
	                  }
	                  
	                  if(warningCount != 0)
	                  {
		                  if(warningCount == 1)
		                  {
		                	  msg += warningCount+" "+getString(R.string.notification_warning_service)+" ";
		                  }
		                  else
		                  {
		                	  msg += warningCount+" "+getString(R.string.notification_warning_services)+" ";
		                  }
	                  }
	                  
	                  if(unknownCount != 0)
	                  {
		                  if(unknownCount == 1)
		                  {
		                	  msg += unknownCount+" "+getString(R.string.notification_unknown_service)+" ";
		                  }
		                  else
		                  {
		                	  msg += unknownCount+" "+getString(R.string.notification_unknown_services)+" ";
		                  }
	                  }
	                  
	                  if(okCount != 0)
	                  {
		                  if(okCount == 1)
		                  {
		                	  msg += okCount+" "+getString(R.string.notification_ok_service)+" ";
		                  }
		                  else
		                  {
		                	  msg += okCount+" "+getString(R.string.notification_ok_services)+" ";
		                  }
	                  }
	                	          
	                  showNotification(msg);
	                	  
	              } else {
	            	  Log.i(getClass().getSimpleName(),"HTTP error: " + response.toString());
	              }
	              httpRequestOustanding.set(false);
	              return response;
	            }
	 
	          });
	        } catch (ClientProtocolException e) {
	        	Log.i(getClass().getSimpleName(),"ClientProtocolException = " + e);
	          e.printStackTrace();
	        } catch (IOException e) {
	        	Log.i(getClass().getSimpleName(),"IOException = " + e);
	          e.printStackTrace();
	        } finally {
	          httpRequestOustanding.set(false);
	        }
	      }
	    };
	    Thread httpThread = new Thread(httpRunnable);
	    httpThread.start();
	    return true;
	  }
}