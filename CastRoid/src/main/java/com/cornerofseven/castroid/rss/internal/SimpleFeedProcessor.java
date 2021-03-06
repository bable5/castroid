/*
   Copyright 2010 Christopher Kruse and Sean Mooney

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.cornerofseven.castroid.rss.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.net.Uri;
import android.util.Log;

import com.cornerofseven.castroid.rss.MalformedRSSException;
import com.cornerofseven.castroid.rss.RSSFeedBuilder;
import com.cornerofseven.castroid.rss.RSSProcessor;
import com.cornerofseven.castroid.rss.RSSTags;
import com.cornerofseven.castroid.rss.feed.RSSItem;

/**
 * An example RSS file:
 * 
 * <rss version="2.0">
 * <channel>
 * 	<title/>
 * 	<item></item>*
 * </channel>
 * </rss>
 * 
 * Remember, we are dealing with untrusted, unknown content.
 * Anything may be on the other end of the feed location. 
 * You have been warned!
 * @author Sean Mooney
 *
 */

public class SimpleFeedProcessor implements RSSProcessor{

	private static final String TAG = "SimpleFeedProcessor";
	
	private RSSFeedBuilder mFeedBuilder = null;
	private URL mFeedLocation;
	
	public SimpleFeedProcessor(URL feedLocation){
		this.mFeedLocation = feedLocation;
	}
	
	/**
	 * Process whatever is on the other end of the URI as an
	 * XML/RSS file. 
	 * <p>
	 * The processor uses the supplied builder to 
	 * construct a representation of the data stored in the RSS file 
	 * for further internal processing/storage in the database.
	 * </p>
	 * <p>
	 * The builder is free to do whatever it wants with the 
	 * data the processor tells it about. For example, the 
	 * data could be placed directly into the database, stored
	 * in an intermediate data-structure or just simply ignored.
	 * </p>
	 * 
	 * @throws ParserConfigurationException -> from underlying parsing used
	 * @throws IOException -> if the file the URI points to does not exist, amount other causes.
	 * @throws SAXException
	 * @throws MalformedRSSException 
	 * 
	 * TODO: Check that document contains specified RSS markers.
	 */
	@Override
	public void process() throws ParserConfigurationException, IOException, SAXException, MalformedRSSException {
		Element root = null;
		
		DocumentBuilder builder 
			= DocumentBuilderFactory.newInstance().newDocumentBuilder();
		InputStream feedstream = getIntputStream();
		Document document = builder.parse(feedstream);
		root = document.getDocumentElement();
		feedstream.close();
		
		processChannel(root);
		mFeedBuilder.finishFeed();
	}

	/**
	 * Convert the feed URI to an inputstream.
	 * The client is responsible for closing the stream.
	 * @return An InputStream from the URI for the feed location.
	 * @throws IOException 
	 */
	protected InputStream getIntputStream() throws IOException{
		return mFeedLocation.openConnection().getInputStream();
	}
	
	@Override
	public void setBuilder(RSSFeedBuilder builder) {
		mFeedBuilder = builder;
	}
	
	@Override
	public RSSFeedBuilder getBuilder(){
		return mFeedBuilder;
	}
	
	/**
	 * Find the RSS node (should be the only one in the document).
	 * @param document The rss document we are processing. This method
	 * assumes the {@code <rss/>} node is a child of document.
	 * @return
	 * @throws MalformedRSSException if cannot find the rss node, or if it finds more than 1 rss node.
	 */
	private Node findRSSRoot(Element document) throws MalformedRSSException{
		Node rss = null;
		
		/*
		 * It may be the case that the starting tree is the RSS node we are looking for,
		 * and not the document containing that node.
		 *
		 * Problem manifested after android 2.3.
		 */
		if(document.getNodeName().equals(RSSTags.RSS))
		    return document;
		
		//Look for the RSS root node in the elements of the current node.
		NodeList nl = document.getElementsByTagName(RSSTags.RSS);
		int numNodes = nl.getLength();
		if(numNodes == 1){
			rss = nl.item(0);
			return rss;
		}
		else if(nl.getLength() == 0){
			throw new MalformedRSSException("No RSS node found.");
		}else{
			throw new MalformedRSSException("Found multiple RSS nodes. Can only have 1 rss node in the document");
		}
	}
	
	/**
	 * Parse the Channel to build up the feed in the factory.
	 * @param root 
	 */
	private void processChannel(Element root) throws MalformedRSSException{
		RSSFeedBuilder builder = mFeedBuilder;
	
		String feedName = "";
		String feedDesc = "";
		String feedLink = "";
		
		//Tell the builder we have created a new RSS Feed.
		builder.newFeed();
		
		//TODO: decide how to handle malformed RSS documents
		//Do we want to throw an exception when a required element is missing?
		//Do we want to just ignore required elements missing? 
		
		//The RSS spec specifies the title, link and description elements must be present, 
		//having a method for this might be overkill...
		Node rss = findRSSRoot(root);
		Node channel = null;
		NodeList children = rss.getChildNodes();
		int numRssChildren = children.getLength();
		if(numRssChildren > 0){
			for(int i =0; i<numRssChildren; i++){
				Node child = children.item(i);
				if(RSSTags.CHANNEL.equals(child.getNodeName())){
					channel = child;
					break; //found the child. we are done!
				}
			}
		}else if(numRssChildren == 0){
			throw new MalformedRSSException("No channel element found");
		}
		
		//process the channel for titl, link, desc and items.
		NodeList channelElements = channel.getChildNodes();
		for(int i = 0; i < channelElements.getLength(); i++){
			Node child = channelElements.item(i);
			String childName = child.getNodeName();
			//choose which child node we have
			if(RSSTags.ITEM.equals(childName)){
				processItem(child);
			}
			else if(RSSTags.CHNL_TITLE.equals(childName)){
				feedName = child.getFirstChild().getNodeValue();
			}else if(RSSTags.CHNL_LINK.equals(childName)){
				feedLink = child.getFirstChild().getNodeValue();
			}else if(RSSTags.CHNL_DESC.equals(childName)){
				feedDesc = child.getFirstChild().getNodeValue();
			}
			//TODO: Write some unit tests.
			else if(RSSTags.CHNL_IMAGE.equals(childName)){
				processImage(child);
			}
		}
		
		
		builder.setChannelTitle(feedName);
		builder.setChannelLink(feedLink);
		builder.setChannelDesc(feedDesc);
		builder.setChannelSource(mFeedLocation.toString());
	}
	
	/**
	 * Process a single item sub-element in the RSS structure.
	 * @param itemNode
	 */
	private void processItem(Node itemNode){
		RSSFeedBuilder builder = mFeedBuilder;
		RSSItem newItem = builder.addItem();
		
		NodeList itemElements = itemNode.getChildNodes();
		for(int i = 0; i < itemElements.getLength(); i++){
			String tmp;
			Node child = itemElements.item(i);
			String childName = child.getNodeName();
			if(RSSTags.ITEM_TITLE.equals(childName)){
				tmp = child.getFirstChild().getNodeValue();
				newItem.setTitle(tmp);
			}else if(RSSTags.ITEM_ENC.equals(childName)){
				processEnclosure(child, newItem);
			}else if(RSSTags.ITEM_DESC.equals(childName)){
				tmp = child.getFirstChild().getNodeValue();
				newItem.setDesc(tmp);
			}else if(RSSTags.ITEM_LINK.equals(childName)){
				tmp = child.getFirstChild().getNodeValue();
				newItem.setLink(tmp);
			}else if(RSSTags.ITEM_PUB_DATE.equals(childName)){
				tmp = child.getFirstChild().getNodeValue();
				String pubDate = parseDate(tmp);
				newItem.setPubDate(pubDate);
			}
		}
	}
	
	/**
	 * 
	 * @param imageNode
	 */
	private void processImage(Node imageNode){
		RSSFeedBuilder builder = mFeedBuilder;
		
		NodeList itemElements = imageNode.getChildNodes();
		for(int i = 0; i < itemElements.getLength(); i++){
			String tmp;
			Node child = itemElements.item(i);
			String childName = child.getNodeName();
			if(RSSTags.IMG_URL.equals(childName)){
				tmp = child.getFirstChild().getNodeValue();
				builder.setChannelImageLink(tmp); 
			}
		}
	}
	
	/**
	 * Convert a date formatted with the RSS spec
	 * into a more simple YYYY-mm-dd formatted.
	 * 
	 * Formatting the date in this manor will make
	 * sorting new items by date easier. The alphabetic
	 * sort is the same as the date sort.
	 * @param rawDate
	 * @return
	 */
	private String parseDate(String rawDate){
		//TODO: if this the only thing that happens, in-line it.
		try {
			return RSSDateParser.parse(rawDate);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * Process an enclosure node.  
	 * An enclosure is of the form:
	 * 
	 * <enclosure url="" length="" type=""/>
	 * 
	 * @param encNode
	 */
	private void processEnclosure(Node encNode, RSSItem newItem){
		//Enclosure enc = new Enclosure();
		String encUrl = "", encType = "";
		long encLength = -1;
		
		//the enclosure's data are stored as attributes
		//of the tag, not as sub-children
		NamedNodeMap attrs = encNode.getAttributes();
		
		/*local named reference for the attribute 
		* we are currently working on. */
		Node attr;
		
		attr = attrs.getNamedItem(RSSTags.ENC_URL);
		if(attr != null){
			encUrl = attr.getNodeValue();
		}
		
		//the length attr represents a integer type.
		attr = attrs.getNamedItem(RSSTags.ENC_LEN);
		if(attr != null){
			String stLen = attr.getNodeValue();
			try{
				encLength = Long.parseLong(stLen);
			}catch(NumberFormatException nfe){
				Log.i(TAG, "Invalid length " + stLen);
			}
		}
		
		attr = attrs.getNamedItem(RSSTags.ENC_TYPE);
		if(attr != null){
			encType = attr.getNodeValue();
		}
		
		newItem.setEnclosure(encUrl);
		newItem.setEnclosureType(encType);
		newItem.setEnclosureLength(encLength);
	}
}
