/*
 * PS3 Media Server, for streaming any medias to your PS3.
 * Copyright (C) 2008  A.Brochard
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.pms.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

import net.pms.PMS;

public class Proxy extends Thread {

	
	private Socket socket, socketToWeb;
	private BufferedReader fromBrowser;
	private OutputStream toBrowser;
	private PrintWriter toWeb;
	private boolean writeCache;

	public Proxy(Socket s, boolean writeCache) throws IOException {
		socket = s;
		fromBrowser = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		toBrowser = socket.getOutputStream();
		this.writeCache = writeCache;
		start();
	}

	public void run() {
		try {
			String getter = null;
			String str, targetHost = "", httpHeader = "";
			while (true) {
				str = fromBrowser.readLine();
				if (str.startsWith("GET") || str.startsWith("POST") || str.startsWith("HEAD")) {
					getter = str;
				}
				if (str.startsWith("Accept-Encoding: gzip")) {
					str = "Accept-Encoding: identity";
				}
				//if (!str.startsWith("If-Modified-Since") && !str.startsWith("Range") && !str.startsWith("If-None-Match"))
					httpHeader += str + "\n";
				if (str.startsWith("Host: "))
					targetHost = str.substring(6);
				if (str.length() == 0)
					break;
			}

			int targetPort = 80;
			String target = targetHost;
			if (targetHost.indexOf(":") > -1) {
				targetPort = Integer.parseInt(targetHost.substring(targetHost.indexOf(":")+1));
				target = targetHost.substring(0, targetHost.indexOf(":"));
			}
			PMS.debug("[PROXY] Trying to connect to: " + target);
			socketToWeb = new Socket(InetAddress.getByName(target), targetPort);
			InputStream sockWebInputStream = socketToWeb.getInputStream();
			toWeb = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socketToWeb.getOutputStream())), true);
			toWeb.println(httpHeader);
			toWeb.flush();
			StringTokenizer st = new StringTokenizer(getter, " ");
			st.nextToken();
			String askedResource = st.nextToken();
			askedResource = askedResource.substring(askedResource.indexOf(targetHost)+targetHost.length());
			PMS.debug("[PROXY] Asked resource: " + askedResource);
			
			String directoryResource = askedResource.substring(0, askedResource.lastIndexOf("/"));
			directoryResource = getWritableFileName(directoryResource);
			String fileResource = askedResource.substring(askedResource.lastIndexOf("/")+1);
			fileResource = getWritableFileName(fileResource);
			fileResource = fileResource + ".cached";
			String fileN = "proxycache/" + target + "/" + directoryResource;
			File directoryResourceFile = new File(fileN);
			if (writeCache)
				directoryResourceFile.mkdirs();
			File cachedResource = new File(directoryResourceFile, fileResource);
			//System.out.println("Trying to find: " + cachedResource.getAbsolutePath());
			
			byte[] buffer = new byte[8192];
			boolean resourceExists = cachedResource.exists() || this.getClass().getResource("/" + fileN) != null;
			boolean inMemory = writeCache && !resourceExists;
		
			FileOutputStream fOUT = null;
			if (resourceExists) {
				PMS.debug("[PROXY] File is cached: " + cachedResource.getAbsolutePath());
				sockWebInputStream.close();
				if (cachedResource.exists())
					sockWebInputStream = new FileInputStream(cachedResource);
				else
					sockWebInputStream = this.getClass().getResourceAsStream("/" + fileN);
			} else if (writeCache) {
				PMS.debug("[PROXY] File is not cached / Writing in it: " + cachedResource.getAbsolutePath());
				fOUT = new FileOutputStream(cachedResource, false);
			}
			
			OutputStream baos = null;
			if (inMemory)
				baos = new ByteArrayOutputStream();
			else
				baos = toBrowser;
			
			
			long total_read = 0;
			
		      int bytes_read;
		      long CL = 10000000000L;
		      while(total_read < CL && (bytes_read = sockWebInputStream.read(buffer)) != -1) {
		    	  if (!resourceExists) {
		    		  if (10000000 == CL ) {
				    	  String s = new String(buffer, 0, bytes_read);
				    	  int clPos = s.indexOf("Content-Length: ");
				    	  if (clPos > -1) {
				    		  CL = Integer.parseInt(s.substring(clPos+16, s.indexOf("\n", clPos)).trim());
				    	  }
			    	  }
			    	  if (bytes_read >= 7) {
			    		  byte end [] = new byte [7];
			    		  System.arraycopy(buffer, bytes_read-7, end, 0, 7);
			    		  if (new String(end).equals("\r\n0\r\n\r\n")) {
			    			  System.out.println("end of transfer chunked");
			    			  CL = -1;
			    		  }
			    	  }
			    	  if (writeCache)
			    		  fOUT.write(buffer, 0, bytes_read);
		    	  }
		    	  baos.write(buffer, 0, bytes_read);
		    	  total_read += bytes_read;
		      }
		      if (inMemory) {
					
		    	  baos.close();
		    	  toBrowser.write(((ByteArrayOutputStream) baos).toByteArray());
					
				}
		      
		      if (writeCache && fOUT != null) {
		    	
					fOUT.close();
				}
		      
		      
		      socketToWeb.close();
//		      baos.close();
		      toBrowser.close();
		} catch (IOException e) {
			//System.err.println("E2 " + Thread.currentThread().getName() + ": " + e.getMessage());
		} finally {
			try {
				if (toWeb != null)
					toWeb.close();
				if (toBrowser != null)
					toBrowser.close();
				socket.close();
			} catch (IOException e) {
				//System.err.println("E3 " + Thread.currentThread().getName() + ": " + e.getMessage());
			}
		}
	}
	
	private String getWritableFileName(String resource) {
		resource = resource.replace('?', '�');
		resource = resource.replace('|', '�');
		resource = resource.replace('/', '�');
		resource = resource.replace('\\', '�');
		return resource;
	}
	

	public static void main(String[] args) throws IOException {
		ServerSocket s = new ServerSocket(8080);
		//System.out.println("Started " + s);
		try {
			while (true) {
				Socket socket = s.accept();
				try {
					new Proxy(socket, false);
				} catch (IOException e) {
					//System.err.println("E1 " + Thread.currentThread().getName() + ": " + e.getMessage());
					socket.close();
				}
			}
		} finally {
			s.close();
		}
	}

}
