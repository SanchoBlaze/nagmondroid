package com.simonmclaughlin.nagios;

import java.util.List;

import android.content.Context;
import android.net.Uri;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.View.OnLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class StatusAdapter extends BaseAdapter {

    private Context context;
    private List<Status> statusList;
	private int rowResID;

    public StatusAdapter(Context context, int rowResID,
						List<Status> statusList) { 
        this.context = context;
		this.rowResID = rowResID;
        this.statusList = statusList;
    }

    public int getCount() {                        
        return statusList.size();
    }

    public Object getItem(int position) {     
        return statusList.get(position);
    }

    public long getItemId(int position) {  
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) { 
    	Status status = statusList.get(position);
		LayoutInflater inflate = LayoutInflater.from( context );
		View v = inflate.inflate( rowResID, parent, false);
		TextView hostView = (TextView)v.findViewById( R.id.host );
		if( hostView != null )
			hostView.setText( status.getHost() );
		TextView infoView = (TextView)v.findViewById( R.id.info );
		if( infoView != null )
			infoView.setText( status.getInfo() );
		TextView serviceView = (TextView)v.findViewById( R.id.service );
		if( serviceView != null )
			serviceView.setText( Uri.decode(status.getService().replace("+", " ")) );
		TextView urlView = (TextView)v.findViewById( R.id.url );
		SpannableString str = SpannableString.valueOf(context.getString(R.string.link_text)); 
		str.setSpan(new URLSpan(status.getNagios()), 0, 25, Spannable.SPAN_INCLUSIVE_EXCLUSIVE); 
		urlView.append(str); 
		addLinkMovementMethod(urlView);
		
	    ImageView statusImage = (ImageView)v.findViewById( R.id.status );
		if( statusImage != null )
			statusImage.setImageResource( status.getStatus() );
		
    	//v.setOnLongClickListener(this.longClick);
    	
        return v;
    }
    
    private static final void addLinkMovementMethod(TextView t) { 
        MovementMethod m = t.getMovementMethod(); 
        if ((m == null) || !(m instanceof LinkMovementMethod)) { 
            if (t.getLinksClickable()) { 
                t.setMovementMethod(LinkMovementMethod.getInstance()); 
            } 
        } 
    } 
}
