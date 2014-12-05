package com.github.herbix.udpdemo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPClient {

	public static void main(String[] args) throws Throwable {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String ip = in.readLine();

		DatagramSocket udpSocket = new DatagramSocket();
		
		for (int i = 0; i <= 50; i++) {
			byte[] data = (i + "").getBytes();
			DatagramPacket packet = new DatagramPacket(data, data.length);
			packet.setAddress(InetAddress.getByName(ip));
			packet.setPort(8888);
			udpSocket.send(packet);
			System.out.println("Send: " + i);
		}
		
		byte[] buffer = new byte[4096];
		DatagramPacket recvpacket = new DatagramPacket(buffer, buffer.length);
		for (int i = 0; i <= 50; i++) {
			udpSocket.receive(recvpacket);
			String s = new String(recvpacket.getData(), recvpacket.getOffset(), recvpacket.getLength());
			System.out.println("Received: " + s);
		}

		udpSocket.close();
	}

}
