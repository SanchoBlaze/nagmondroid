package com.simonmclaughlin.nagios;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


public class XMLParserHandler extends DefaultHandler {

     private boolean in_host = false;
     private boolean in_info = false;
     private boolean in_status = false;
     private boolean in_service = false;
     private boolean in_nagios = false;
     private String host;
     private String info;
     private String status;
     private String service;
     private String nagios;
     
     private ArrayList<Status> data = new ArrayList<Status>();

     public ArrayList<Status> getData() {
    	 return data;
     }

     public void parse(String xml) throws ParserConfigurationException, SAXException, IOException {
         SAXParserFactory spf = null;
         SAXParser sp = null;
        
         spf = SAXParserFactory.newInstance();
         if (spf != null) {
                 sp = spf.newSAXParser();
             sp.parse(new InputSource(new StringReader(xml)), this);
         }
     }

     /** Gets be called on opening tags like:
      * <tag>
      * Can provide attribute(s), when xml was like:
      * <tag attribute="attributeValue">*/
     @Override
     public void startElement(String namespaceURI, String localName,
               String qName, Attributes atts) throws SAXException {
          if (localName.equals("host")) {
               this.in_host = true;
          }else if (localName.equals("level")) {
               this.in_status = true;
          }else if (localName.equals("info")) {
               this.in_info = true;
          }else if (localName.equals("service")) {
               this.in_service = true;
          }else if (localName.equals("nagios")) {
               this.in_nagios = true;
          }
     }
     
     /** Gets be called on closing tags like:
      * </tag> */
     @Override
     public void endElement(String namespaceURI, String localName, String qName)
               throws SAXException {
          if (localName.equals("host")) {
               this.in_host = false;
          }else if (localName.equals("level")) {
               this.in_status = false;
          }else if (localName.equals("info")) {
               this.in_info = false;
          }else if (localName.equals("service")) {
               this.in_service = false;
          }else if (localName.equals("nagios")) {
               this.in_nagios = false;
          }else if (localName.equals("problem")) {
        	  Status s = new Status(host, status, info, service, nagios);
        	  data.add(s);
          }
     }
     
     /** Gets be called on the following structure:
      * <tag>characters</tag> */
     @Override
    public void characters(char ch[], int start, int length) {
          if(this.in_host){
        	  this.host = new String(ch, start, length);
          }else if (this.in_status) {
        	  this.status = new String(ch, start, length);
          }else if (this.in_info) {
        	  this.info = new String(ch, start, length);
          }else if (this.in_service) {
        	  this.service = new String(ch, start, length);
          }else if (this.in_nagios) {
        	  this.nagios = new String(ch, start, length);
          }
    } 
}
