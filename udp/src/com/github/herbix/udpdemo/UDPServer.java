package com.github.herbix.udpdemo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServer {

	public static void main(String[] args) throws Throwable {
		DatagramSocket udpSocket = new DatagramSocket(8888);

		byte[] buffer = new byte[4096];		
		DatagramPacket packet = new DatagramPacket(buffer, 4096);
		int i = 0;
		
		while(true) {
			udpSocket.receive(packet);
			i++;
			String s = new String(packet.getData(), packet.getOffset(), packet.getLength());
			System.out.println("Received: " + s);
			s = i + " " + s;
			byte[] retbuffer = s.getBytes();
			DatagramPacket retpacket = new DatagramPacket(retbuffer, retbuffer.length, packet.getAddress(), packet.getPort());
			System.out.println("Send: " + s);
			udpSocket.send(retpacket);
		}
	}

}
