package com.simonmclaughlin.nagios;


public class Status {
	
	private String host;
	private String status;
	private String info;
	private String service;
	private String nagios;
	
	public Status(String host, String status, String info, String service, String nagios) {
		this.host = host;
		this.info = info;
		this.status = status;
		this.service = service;
		this.nagios = nagios;
	}
	
	public Status() {
	}
	
	public String getHost()
	{
		return host;
	}
	
	public int getStatus()
	{
		if( status.equalsIgnoreCase("unknown") ) {
				return R.drawable.unknown;
		}
		else if( status.equalsIgnoreCase("critical") ) {
				return R.drawable.critical;
		}
		else if( status.equalsIgnoreCase("warning") ) {
				return R.drawable.warning;
		}
		else if( status.equalsIgnoreCase("ok") ) {
			return R.drawable.ok;
		}
		return 0;
	}
	
	public String getInfo()
	{
		return info;
	}
	
	public String getService()
	{
		return service;
	}
	
	public String getNagios()
	{
		return nagios.replace("status", "extinfo")+"?type=2&host="+this.getHost()+"&service="+this.getService();
	}
}
